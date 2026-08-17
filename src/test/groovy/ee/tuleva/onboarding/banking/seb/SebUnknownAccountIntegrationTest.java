package ee.tuleva.onboarding.banking.seb;

import static ee.tuleva.onboarding.banking.BankType.SEB;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.banking.event.BankMessageEvents.ProcessBankMessagesRequested;
import ee.tuleva.onboarding.banking.message.BankingMessage;
import ee.tuleva.onboarding.banking.message.BankingMessageRepository;
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentRepository;
import java.io.IOException;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

@SebIntegrationTest
class SebUnknownAccountIntegrationTest {

  @Autowired private BankingMessageRepository bankingMessageRepository;
  @Autowired private SavingFundPaymentRepository savingFundPaymentRepository;
  @Autowired private ApplicationEventPublisher eventPublisher;

  @Test
  void statementFromUnknownAccount_failsTheMessageAndBooksNothing() throws IOException {
    // Rewrite the fixture's IBAN to one no registry entry can match
    var xml =
        loadFixture("eod-transactions-response.xml")
            .replace("EE651010220306497226", "EE009999999999999999");
    var message =
        bankingMessageRepository.save(
            BankingMessage.builder()
                .bankType(SEB)
                .requestId("unknown-account-test")
                .trackingId("unknown-account-test")
                .rawResponse(xml)
                .timezone("Europe/Tallinn")
                .build());

    eventPublisher.publishEvent(new ProcessBankMessagesRequested());

    var processed = bankingMessageRepository.findById(message.getId()).orElseThrow();
    assertThat(processed.getFailedAt()).isNotNull();
    assertThat(processed.getProcessedAt()).isNull();
    assertThat(savingFundPaymentRepository.findAll()).isEmpty();
  }

  private String loadFixture(String filename) throws IOException {
    try (var stream =
        Objects.requireNonNull(getClass().getResourceAsStream("/banking/seb/" + filename))) {
      return new String(stream.readAllBytes(), UTF_8);
    }
  }
}
