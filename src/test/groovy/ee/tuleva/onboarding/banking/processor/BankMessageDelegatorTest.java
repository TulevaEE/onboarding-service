package ee.tuleva.onboarding.banking.processor;

import static ee.tuleva.onboarding.banking.BankType.SWEDBANK;
import static ee.tuleva.onboarding.swedbank.Swedbank.SWEDBANK_GATEWAY_TIME_ZONE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.banking.event.BankMessageEvents.BankStatementReceived;
import ee.tuleva.onboarding.banking.message.BankingMessage;
import ee.tuleva.onboarding.banking.message.BankingMessageRepository;
import ee.tuleva.onboarding.banking.statement.BankStatement;
import ee.tuleva.onboarding.banking.statement.BankStatement.BankStatementType;
import ee.tuleva.onboarding.banking.statement.BankStatementAccount;
import ee.tuleva.onboarding.banking.statement.BankStatementExtractor;
import ee.tuleva.onboarding.time.TestClockHolder;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class BankMessageDelegatorTest {

  private Clock clock;
  private BankingMessageRepository bankingMessageRepository;
  private BankStatementExtractor bankStatementExtractor;
  private ApplicationEventPublisher eventPublisher;
  private BankMessageDelegator delegator;

  @BeforeEach
  void setUp() {
    clock = TestClockHolder.clock;
    bankingMessageRepository = mock(BankingMessageRepository.class);
    bankStatementExtractor = mock(BankStatementExtractor.class);
    eventPublisher = mock(ApplicationEventPublisher.class);

    delegator =
        new BankMessageDelegator(
            clock, bankingMessageRepository, bankStatementExtractor, eventPublisher);
  }

  @Test
  @DisplayName("Publishes event for intra-day report message")
  void publishesEventForIntraDayReport() {
    var messageId = UUID.randomUUID();
    var xml = intraDayReportXml();
    var message =
        BankingMessage.builder()
            .bankType(SWEDBANK)
            .id(messageId)
            .requestId("test")
            .trackingId("test")
            .rawResponse(xml)
            .timezone(SWEDBANK_GATEWAY_TIME_ZONE.getId())
            .receivedAt(clock.instant())
            .build();
    var bankStatement = createBankStatement(BankStatementType.INTRA_DAY_REPORT);

    when(bankingMessageRepository
            .findAllByProcessedAtIsNullAndFailedAtIsNullOrderByReceivedAtDesc())
        .thenReturn(List.of(message));
    when(bankStatementExtractor.extractFromIntraDayReport(eq(xml), any()))
        .thenReturn(bankStatement);

    delegator.processMessages();

    var eventCaptor = ArgumentCaptor.forClass(BankStatementReceived.class);
    verify(eventPublisher).publishEvent(eventCaptor.capture());

    var event = eventCaptor.getValue();
    assertThat(event.messageId()).isEqualTo(messageId);
    assertThat(event.bankType()).isEqualTo(SWEDBANK);
    assertThat(event.statement()).isEqualTo(bankStatement);

    assertThat(message.getProcessedAt()).isEqualTo(clock.instant());
    verify(bankingMessageRepository).save(message);
  }

  @Test
  @DisplayName("Publishes event for historic statement message")
  void publishesEventForHistoricStatement() {
    var messageId = UUID.randomUUID();
    var xml = historicStatementXml();
    var message =
        BankingMessage.builder()
            .bankType(SWEDBANK)
            .id(messageId)
            .requestId("test")
            .trackingId("test")
            .rawResponse(xml)
            .timezone(SWEDBANK_GATEWAY_TIME_ZONE.getId())
            .receivedAt(clock.instant())
            .build();
    var bankStatement = createBankStatement(BankStatementType.HISTORIC_STATEMENT);

    when(bankingMessageRepository
            .findAllByProcessedAtIsNullAndFailedAtIsNullOrderByReceivedAtDesc())
        .thenReturn(List.of(message));
    when(bankStatementExtractor.extractFromHistoricStatement(eq(xml), any()))
        .thenReturn(bankStatement);

    delegator.processMessages();

    var eventCaptor = ArgumentCaptor.forClass(BankStatementReceived.class);
    verify(eventPublisher).publishEvent(eventCaptor.capture());

    var event = eventCaptor.getValue();
    assertThat(event.messageId()).isEqualTo(messageId);
    assertThat(event.bankType()).isEqualTo(SWEDBANK);
    assertThat(event.statement()).isEqualTo(bankStatement);

    assertThat(message.getProcessedAt()).isEqualTo(clock.instant());
    verify(bankingMessageRepository).save(message);
  }

  @Test
  @DisplayName("Logs and marks as processed for payment order confirmation")
  void logsPaymentOrderConfirmation() {
    var message =
        BankingMessage.builder()
            .bankType(SWEDBANK)
            .id(UUID.randomUUID())
            .requestId("test")
            .trackingId("test")
            .rawResponse(paymentOrderConfirmationXml())
            .timezone(SWEDBANK_GATEWAY_TIME_ZONE.getId())
            .receivedAt(clock.instant())
            .build();

    when(bankingMessageRepository
            .findAllByProcessedAtIsNullAndFailedAtIsNullOrderByReceivedAtDesc())
        .thenReturn(List.of(message));

    delegator.processMessages();

    verify(eventPublisher, never()).publishEvent(any());
    assertThat(message.getProcessedAt()).isEqualTo(clock.instant());
    verify(bankingMessageRepository).save(message);
  }

  @Test
  @DisplayName("Marks message as failed when processing throws exception")
  void marksMessageAsFailedOnException() {
    var xml = intraDayReportXml();
    var message =
        BankingMessage.builder()
            .bankType(SWEDBANK)
            .id(UUID.randomUUID())
            .requestId("test")
            .trackingId("test")
            .rawResponse(xml)
            .timezone(SWEDBANK_GATEWAY_TIME_ZONE.getId())
            .receivedAt(clock.instant())
            .build();

    when(bankingMessageRepository
            .findAllByProcessedAtIsNullAndFailedAtIsNullOrderByReceivedAtDesc())
        .thenReturn(List.of(message));
    when(bankStatementExtractor.extractFromIntraDayReport(eq(xml), any()))
        .thenThrow(new RuntimeException("Extraction failed"));

    delegator.processMessages();

    verify(eventPublisher, never()).publishEvent(any());
    assertThat(message.getFailedAt()).isEqualTo(clock.instant());
    assertThat(message.getProcessedAt()).isNull();
    verify(bankingMessageRepository).save(message);
  }

  private String intraDayReportXml() {
    return "<?xml version=\"1.0\" encoding=\"UTF-8\"?> <Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:camt.052.001.02\"></Document>";
  }

  private String historicStatementXml() {
    return "<?xml version=\"1.0\" encoding=\"UTF-8\"?> <Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:camt.053.001.02\"></Document>";
  }

  private String paymentOrderConfirmationXml() {
    return "<?xml version=\"1.0\" encoding=\"UTF-8\"?> <Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pain.002.001.10\"></Document>";
  }

  private BankStatement createBankStatement(BankStatementType type) {
    return new BankStatement(
        type,
        new BankStatementAccount("EE442200221092874625", "Tuleva Fondid AS", "14118923"),
        List.of(),
        List.of(),
        Instant.now());
  }
}
