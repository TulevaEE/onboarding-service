package ee.tuleva.onboarding.banking.seb.fetcher;

import static ee.tuleva.onboarding.banking.BankAccountType.DEPOSIT_EUR;
import static ee.tuleva.onboarding.banking.BankAccountType.WITHDRAWAL_EUR;
import static ee.tuleva.onboarding.banking.BankType.SEB;
import static ee.tuleva.onboarding.banking.seb.Seb.SEB_GATEWAY_TIME_ZONE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.banking.message.BankingMessage;
import ee.tuleva.onboarding.banking.message.BankingMessageRepository;
import ee.tuleva.onboarding.banking.seb.SebGatewayClient;
import ee.tuleva.onboarding.config.TestSchedulerLockConfiguration;
import ee.tuleva.onboarding.time.ClockHolder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
@Import(TestSchedulerLockConfiguration.class)
@TestPropertySource(
    properties = {
      "swedbank-gateway.enabled=false",
      "seb-gateway.enabled=true",
      "seb-gateway.url=https://test.example.com",
      "seb-gateway.orgId=test-org",
      "seb-gateway.keystore.path=src/test/resources/banking/seb/test-seb-gateway.p12",
      "seb-gateway.keystore.password=testpass",
      "seb-gateway.accounts.DEPOSIT_EUR=EE123456789012345678",
      "seb-gateway.accounts.WITHDRAWAL_EUR=EE987654321098765432",
      "seb-gateway.accounts.FUND_INVESTMENT_EUR=EE111222333444555666"
    })
class SebBankMessagesFetchingIntegrationTest {

  private static final String DEPOSIT_IBAN = "EE123456789012345678";
  private static final String WITHDRAWAL_IBAN = "EE987654321098765432";

  @MockitoBean private SebGatewayClient sebGatewayClient;

  @Autowired private SebStatementFetcher sebStatementFetcher;

  @Autowired private BankingMessageRepository bankingMessageRepository;

  @AfterEach
  void tearDown() {
    ClockHolder.setDefaultClock();
  }

  @Test
  void fetchCurrentDayTransactions_persistsMessageToDatabase() throws Exception {
    String testXml = loadTestXml("current-transactions-response.xml");
    when(sebGatewayClient.getCurrentTransactions(DEPOSIT_IBAN)).thenReturn(testXml);

    sebStatementFetcher.fetchCurrentDayTransactions(DEPOSIT_EUR);

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

    sebStatementFetcher.fetchEodTransactions(WITHDRAWAL_EUR);

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

    sebStatementFetcher.fetchHistoricTransactions(DEPOSIT_EUR, dateFrom, dateTo);

    List<BankingMessage> messages = findAllUnprocessedMessages();
    assertThat(messages).hasSize(1);

    BankingMessage message = messages.getFirst();
    assertThat(message.getBankType()).isEqualTo(SEB);
    assertThat(message.getRawResponse()).isEqualTo(testXml);
    assertThat(message.getTimezoneId()).isEqualTo(SEB_GATEWAY_TIME_ZONE);
  }

  @Test
  void fetchLast7DaysTransactions_persistsMessageToDatabase() throws Exception {
    String testXml = loadTestXml("historical-transactions-response.xml");
    LocalDate today = LocalDate.of(2025, 1, 15);
    LocalDate sevenDaysAgo = LocalDate.of(2025, 1, 8);
    ClockHolder.setClock(
        Clock.fixed(today.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC));

    when(sebGatewayClient.getTransactions(DEPOSIT_IBAN, sevenDaysAgo, today)).thenReturn(testXml);

    sebStatementFetcher.fetchLast7DaysTransactions(DEPOSIT_EUR);

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
