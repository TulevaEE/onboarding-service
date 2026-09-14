package ee.tuleva.onboarding.banking.processor;

import static ee.tuleva.onboarding.banking.BankType.SEB;
import static ee.tuleva.onboarding.banking.message.BankMessageType.HISTORIC_STATEMENT;
import static ee.tuleva.onboarding.banking.message.BankMessageType.PAYMENT_ORDER_CONFIRMATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import ee.tuleva.onboarding.banking.event.BankMessageEvents.BankMessagesProcessingCompleted;
import ee.tuleva.onboarding.banking.event.BankMessageEvents.ProcessBankMessagesRequested;
import ee.tuleva.onboarding.banking.message.BankingMessage;
import ee.tuleva.onboarding.banking.message.BankingMessageRepository;
import ee.tuleva.onboarding.banking.statement.BankStatement;
import ee.tuleva.onboarding.banking.statement.BankStatementAccount;
import ee.tuleva.onboarding.banking.statement.BankStatementExtractor;
import ee.tuleva.onboarding.banking.statement.BankStatementParseException;
import ee.tuleva.onboarding.banking.statement.StatementPeriod;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class BankMessageDelegatorTest {

  private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

  @Mock private BankingMessageRepository bankingMessageRepository;
  @Mock private BankStatementExtractor bankStatementExtractor;
  @Mock private ApplicationEventPublisher eventPublisher;

  @Test
  void onProcessRequested_publishesCompletionEventAfterProcessingAllMessages() {
    given(
            bankingMessageRepository
                .findAllByProcessedAtIsNullAndFailedAtIsNullOrderByReceivedAtDesc())
        .willReturn(List.of());
    var delegator =
        new BankMessageDelegator(
            clock, bankingMessageRepository, bankStatementExtractor, eventPublisher);

    delegator.onProcessRequested(new ProcessBankMessagesRequested());

    then(eventPublisher).should().publishEvent(new BankMessagesProcessingCompleted());
  }

  @Test
  void onProcessRequested_recordsTheStatementTypeAccountAndPeriodOnTheProcessedMessage() {
    var rawXml =
        "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:camt.053.001.02\"><BkToCstmrStmt/></Document>";
    var message = sebMessage(rawXml);
    var statement =
        new BankStatement(
            BankStatement.BankStatementType.HISTORIC_STATEMENT,
            new BankStatementAccount("EE001234567890123456", "Acme OÜ", "10060701"),
            List.of(),
            List.of(),
            new StatementPeriod(LocalDate.of(2026, 9, 11), LocalDate.of(2026, 9, 13)));
    given(
            bankingMessageRepository
                .findAllByProcessedAtIsNullAndFailedAtIsNullOrderByReceivedAtDesc())
        .willReturn(List.of(message));
    given(bankStatementExtractor.extractFromHistoricStatement(rawXml, ZoneId.of("Europe/Tallinn")))
        .willReturn(statement);
    var delegator =
        new BankMessageDelegator(
            clock, bankingMessageRepository, bankStatementExtractor, eventPublisher);

    delegator.onProcessRequested(new ProcessBankMessagesRequested());

    assertThat(message.getMessageType()).isEqualTo(HISTORIC_STATEMENT);
    assertThat(message.getAccountIban()).isEqualTo("EE001234567890123456");
    assertThat(message.getStatementFrom()).isEqualTo(LocalDate.of(2026, 9, 11));
    assertThat(message.getStatementTo()).isEqualTo(LocalDate.of(2026, 9, 13));
    assertThat(message.getProcessedAt()).isEqualTo(clock.instant());
    then(bankingMessageRepository).should().save(message);
  }

  @Test
  void onProcessRequested_recordsOnlyTheTypeForPaymentOrderConfirmations() {
    var rawXml =
        "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pain.002.001.10\"><CstmrPmtStsRpt/></Document>";
    var message = sebMessage(rawXml);
    given(
            bankingMessageRepository
                .findAllByProcessedAtIsNullAndFailedAtIsNullOrderByReceivedAtDesc())
        .willReturn(List.of(message));
    var delegator =
        new BankMessageDelegator(
            clock, bankingMessageRepository, bankStatementExtractor, eventPublisher);

    delegator.onProcessRequested(new ProcessBankMessagesRequested());

    assertThat(message.getMessageType()).isEqualTo(PAYMENT_ORDER_CONFIRMATION);
    assertThat(message.getAccountIban()).isNull();
    assertThat(message.getStatementFrom()).isNull();
    assertThat(message.getStatementTo()).isNull();
    assertThat(message.getProcessedAt()).isEqualTo(clock.instant());
  }

  @Test
  void onProcessRequested_keepsTheCoverageRecordedAtFetchTimeWhenExtractionFails() {
    var rawXml =
        "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:camt.053.001.02\"><BkToCstmrStmt/></Document>";
    var message = sebMessage(rawXml);
    message.setMessageType(HISTORIC_STATEMENT);
    message.setAccountIban("EE001234567890123456");
    message.setStatementFrom(LocalDate.of(2026, 9, 11));
    message.setStatementTo(LocalDate.of(2026, 9, 11));
    given(
            bankingMessageRepository
                .findAllByProcessedAtIsNullAndFailedAtIsNullOrderByReceivedAtDesc())
        .willReturn(List.of(message));
    given(bankStatementExtractor.extractFromHistoricStatement(rawXml, ZoneId.of("Europe/Tallinn")))
        .willThrow(new BankStatementParseException("Bank statement integrity check failed"));
    var delegator =
        new BankMessageDelegator(
            clock, bankingMessageRepository, bankStatementExtractor, eventPublisher);

    delegator.onProcessRequested(new ProcessBankMessagesRequested());

    assertThat(message.getFailedAt()).isEqualTo(clock.instant());
    assertThat(message.getProcessedAt()).isNull();
    assertThat(message.getMessageType()).isEqualTo(HISTORIC_STATEMENT);
    assertThat(message.getAccountIban()).isEqualTo("EE001234567890123456");
    assertThat(message.getStatementFrom()).isEqualTo(LocalDate.of(2026, 9, 11));
    assertThat(message.getStatementTo()).isEqualTo(LocalDate.of(2026, 9, 11));
  }

  private static BankingMessage sebMessage(String rawXml) {
    return BankingMessage.builder()
        .id(UUID.randomUUID())
        .bankType(SEB)
        .requestId("req-1")
        .trackingId("trk-1")
        .rawResponse(rawXml)
        .timezone("Europe/Tallinn")
        .build();
  }
}
