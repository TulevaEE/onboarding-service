package ee.tuleva.onboarding.banking.seb.fetcher;

import static ee.tuleva.onboarding.banking.BankAccountType.DEPOSIT_EUR;
import static ee.tuleva.onboarding.banking.BankAccountType.WITHDRAWAL_EUR;
import static ee.tuleva.onboarding.banking.BankType.SEB;
import static ee.tuleva.onboarding.banking.seb.Seb.SEB_GATEWAY_TIME_ZONE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.banking.event.BankMessageEvents.FetchSebCurrentDayTransactionsRequested;
import ee.tuleva.onboarding.banking.event.BankMessageEvents.FetchSebEodTransactionsRequested;
import ee.tuleva.onboarding.banking.event.BankMessageEvents.FetchSebHistoricTransactionsRequested;
import ee.tuleva.onboarding.banking.message.BankingMessage;
import ee.tuleva.onboarding.banking.message.BankingMessageRepository;
import ee.tuleva.onboarding.banking.seb.SebGatewayClient;
import ee.tuleva.onboarding.banking.seb.SebIntegrationTest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SebIntegrationTest
@TestPropertySource(
    properties = {
      "seb-gateway.accounts.DEPOSIT_EUR=EE123456789012345678",
      "seb-gateway.accounts.WITHDRAWAL_EUR=EE987654321098765432",
      "seb-gateway.accounts.FUND_INVESTMENT_EUR=EE111222333444555666"
    })
class SebBankMessagesFetchingIntegrationTest {

  private static final String DEPOSIT_IBAN = "EE123456789012345678";
  private static final String WITHDRAWAL_IBAN = "EE987654321098765432";

  @MockitoBean private SebGatewayClient sebGatewayClient;

  @Autowired private ApplicationEventPublisher eventPublisher;

  @Autowired private BankingMessageRepository bankingMessageRepository;

  @Test
  void fetchCurrentDayTransactions_persistsMessageToDatabase() throws Exception {
    String testXml = loadTestXml("current-transactions-response.xml");
    when(sebGatewayClient.getCurrentTransactions(DEPOSIT_IBAN)).thenReturn(testXml);

    eventPublisher.publishEvent(new FetchSebCurrentDayTransactionsRequested(DEPOSIT_EUR));

    List<BankingMessage> messages = findAllUnprocessedMessages();
    assertThat(messages).hasSize(1);

    BankingMessage message = messages.getFirst();
    assertThat(message.getBankType()).isEqualTo(SEB);
    assertThat(message.getRawResponse()).isEqualTo(testXml);
    assertThat(message.getTimezoneId()).isEqualTo(SEB_GATEWAY_TIME_ZONE);
    assertThat(message.getProcessedAt()).isNull();
    assertThat(message.getFailedAt()).isNull();
  }

  @Test
  void fetchEodTransactions_persistsMessageToDatabase() throws Exception {
    String testXml = loadTestXml("eod-transactions-response.xml");
    when(sebGatewayClient.getEodTransactions(WITHDRAWAL_IBAN)).thenReturn(testXml);

    eventPublisher.publishEvent(new FetchSebEodTransactionsRequested(WITHDRAWAL_EUR));

    List<BankingMessage> messages = findAllUnprocessedMessages();
    assertThat(messages).hasSize(1);

    BankingMessage message = messages.getFirst();
    assertThat(message.getBankType()).isEqualTo(SEB);
    assertThat(message.getRawResponse()).isEqualTo(testXml);
    assertThat(message.getTimezoneId()).isEqualTo(SEB_GATEWAY_TIME_ZONE);
  }

  @Test
  void fetchHistoricTransactions_persistsMessageToDatabase() throws Exception {
    String testXml = loadTestXml("historical-transactions-response.xml");
    LocalDate dateFrom = LocalDate.of(2024, 1, 1);
    LocalDate dateTo = LocalDate.of(2024, 1, 31);
    when(sebGatewayClient.getTransactions(DEPOSIT_IBAN, dateFrom, dateTo)).thenReturn(testXml);

    eventPublisher.publishEvent(
        new FetchSebHistoricTransactionsRequested(DEPOSIT_EUR, dateFrom, dateTo));

    List<BankingMessage> messages = findAllUnprocessedMessages();
    assertThat(messages).hasSize(1);

    BankingMessage message = messages.getFirst();
    assertThat(message.getBankType()).isEqualTo(SEB);
    assertThat(message.getRawResponse()).isEqualTo(testXml);
    assertThat(message.getTimezoneId()).isEqualTo(SEB_GATEWAY_TIME_ZONE);
  }

  private List<BankingMessage> findAllUnprocessedMessages() {
    return bankingMessageRepository
        .findAllByProcessedAtIsNullAndFailedAtIsNullOrderByReceivedAtDesc();
  }

  private String loadTestXml(String filename) throws Exception {
    return Files.readString(Path.of("src/test/resources/banking/seb/" + filename));
  }
}
