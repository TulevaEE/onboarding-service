package ee.tuleva.onboarding.banking.seb.fetcher;

import static ee.tuleva.onboarding.banking.BankAccountType.DEPOSIT_EUR;
import static ee.tuleva.onboarding.banking.BankAccountType.WITHDRAWAL_EUR;
import static ee.tuleva.onboarding.banking.BankType.SEB;
import static ee.tuleva.onboarding.banking.seb.Seb.SEB_GATEWAY_TIME_ZONE;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.banking.BankAccountType;
import ee.tuleva.onboarding.banking.message.BankingMessageRepository;
import ee.tuleva.onboarding.banking.seb.SebAccountConfiguration;
import ee.tuleva.onboarding.banking.seb.SebGatewayClient;
import ee.tuleva.onboarding.time.ClockHolder;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SebStatementFetcherTest {

  private static final String DEPOSIT_IBAN = "EE_DEPOSIT_IBAN";
  private static final String WITHDRAWAL_IBAN = "EE_WITHDRAWAL_IBAN";

  private SebGatewayClient sebGatewayClient;
  private SebAccountConfiguration sebAccountConfiguration;
  private BankingMessageRepository bankingMessageRepository;
  private SebStatementFetcher fetcher;

  @BeforeEach
  void setup() {
    sebGatewayClient = mock(SebGatewayClient.class);
    sebAccountConfiguration = mock(SebAccountConfiguration.class);
    bankingMessageRepository = mock(BankingMessageRepository.class);

    fetcher =
        new SebStatementFetcher(
            sebGatewayClient, sebAccountConfiguration, bankingMessageRepository);
  }

  @AfterEach
  void tearDown() {
    ClockHolder.setDefaultClock();
  }

  @Test
  void fetchCurrentDayTransactions_persistsForSingleAccount() {
    String rawXml = "<xml>current-day-data</xml>";
    when(sebAccountConfiguration.getAccountIban(DEPOSIT_EUR)).thenReturn(DEPOSIT_IBAN);
    when(sebGatewayClient.getCurrentTransactions(DEPOSIT_IBAN)).thenReturn(rawXml);

    fetcher.fetchCurrentDayTransactions(DEPOSIT_EUR);

    verify(bankingMessageRepository)
        .save(
            argThat(
                message ->
                    message.getBankType() == SEB
                        && message.getRawResponse().equals(rawXml)
                        && message.getTimezoneId().equals(SEB_GATEWAY_TIME_ZONE)));
  }

  @Test
  void fetchCurrentDayTransactions_continuesOnError() {
    when(sebAccountConfiguration.getAccountIban(any())).thenReturn(DEPOSIT_IBAN);
    when(sebGatewayClient.getCurrentTransactions(any())).thenThrow(new RuntimeException("Error"));

    fetcher.fetchCurrentDayTransactions();

    verify(sebGatewayClient, times(BankAccountType.values().length)).getCurrentTransactions(any());
  }

  @Test
  void fetchEodTransactions_persistsForSingleAccount() {
    String rawXml = "<xml>eod-data</xml>";
    when(sebAccountConfiguration.getAccountIban(WITHDRAWAL_EUR)).thenReturn(WITHDRAWAL_IBAN);
    when(sebGatewayClient.getEodTransactions(WITHDRAWAL_IBAN)).thenReturn(rawXml);

    fetcher.fetchEodTransactions(WITHDRAWAL_EUR);

    verify(bankingMessageRepository)
        .save(
            argThat(
                message ->
                    message.getBankType() == SEB
                        && message.getRawResponse().equals(rawXml)
                        && message.getTimezoneId().equals(SEB_GATEWAY_TIME_ZONE)));
  }

  @Test
  void fetchEodTransactions_continuesOnError() {
    when(sebAccountConfiguration.getAccountIban(any())).thenReturn(DEPOSIT_IBAN);
    when(sebGatewayClient.getEodTransactions(any())).thenThrow(new RuntimeException("Error"));

    fetcher.fetchEodTransactions();

    verify(sebGatewayClient, times(BankAccountType.values().length)).getEodTransactions(any());
  }

  @Test
  void fetchHistoricTransactions_persistsWithDateRange() {
    String rawXml = "<xml>historic-data</xml>";
    LocalDate dateFrom = LocalDate.of(2024, 1, 1);
    LocalDate dateTo = LocalDate.of(2024, 1, 31);
    when(sebAccountConfiguration.getAccountIban(DEPOSIT_EUR)).thenReturn(DEPOSIT_IBAN);
    when(sebGatewayClient.getTransactions(DEPOSIT_IBAN, dateFrom, dateTo)).thenReturn(rawXml);

    fetcher.fetchHistoricTransactions(DEPOSIT_EUR, dateFrom, dateTo);

    verify(sebGatewayClient).getTransactions(DEPOSIT_IBAN, dateFrom, dateTo);
    verify(bankingMessageRepository)
        .save(
            argThat(
                message ->
                    message.getBankType() == SEB
                        && message.getRawResponse().equals(rawXml)
                        && message.getTimezoneId().equals(SEB_GATEWAY_TIME_ZONE)));
  }

  @Test
  void fetchLast7DaysTransactions_fetchesCorrectDateRange() {
    String rawXml = "<xml>last-7-days-data</xml>";
    LocalDate today = LocalDate.of(2025, 1, 15);
    LocalDate sevenDaysAgo = LocalDate.of(2025, 1, 8);
    ClockHolder.setClock(
        Clock.fixed(today.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC));

    when(sebAccountConfiguration.getAccountIban(DEPOSIT_EUR)).thenReturn(DEPOSIT_IBAN);
    when(sebGatewayClient.getTransactions(DEPOSIT_IBAN, sevenDaysAgo, today)).thenReturn(rawXml);

    fetcher.fetchLast7DaysTransactions(DEPOSIT_EUR);

    verify(sebGatewayClient).getTransactions(DEPOSIT_IBAN, sevenDaysAgo, today);
    verify(bankingMessageRepository)
        .save(
            argThat(
                message ->
                    message.getBankType() == SEB
                        && message.getRawResponse().equals(rawXml)
                        && message.getTimezoneId().equals(SEB_GATEWAY_TIME_ZONE)));
  }
}
