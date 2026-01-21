package ee.tuleva.onboarding.banking.seb.fetcher;

import static ee.tuleva.onboarding.banking.BankType.SEB;
import static ee.tuleva.onboarding.banking.seb.Seb.SEB_GATEWAY_TIME_ZONE;
import static ee.tuleva.onboarding.time.ClockHolder.clock;

import ee.tuleva.onboarding.banking.BankAccountType;
import ee.tuleva.onboarding.banking.message.BankingMessage;
import ee.tuleva.onboarding.banking.message.BankingMessageRepository;
import ee.tuleva.onboarding.banking.seb.SebAccountConfiguration;
import ee.tuleva.onboarding.banking.seb.SebGatewayClient;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

@RequiredArgsConstructor
@Slf4j
public class SebStatementFetcher {

  private final SebGatewayClient sebGatewayClient;
  private final SebAccountConfiguration sebAccountConfiguration;
  private final BankingMessageRepository bankingMessageRepository;

  // @Scheduled(cron = "0 0 9-17 * * MON-FRI", zone = "Europe/Tallinn")
  @SchedulerLock(
      name = "SebStatementFetcher_fetchCurrentDayTransactions",
      lockAtMostFor = "23h",
      lockAtLeastFor = "30m")
  public void fetchCurrentDayTransactions() {
    log.info("Running SEB current day transactions fetcher");
    for (BankAccountType account : BankAccountType.values()) {
      try {
        fetchCurrentDayTransactions(account);
      } catch (Exception exception) {
        log.error("SEB current day transactions fetch failed: account={}", account, exception);
      }
    }
  }

  public void fetchCurrentDayTransactions(BankAccountType account) {
    String iban = sebAccountConfiguration.getAccountIban(account);
    log.info("Fetching SEB current day transactions: account={}, iban={}", account, iban);

    String rawXml = sebGatewayClient.getCurrentTransactions(iban);
    persistBankingMessage(rawXml);
  }

  // @Scheduled(cron = "0 0 18 * * *", zone = "Europe/Tallinn")
  @SchedulerLock(
      name = "SebStatementFetcher_fetchEodTransactions",
      lockAtMostFor = "23h",
      lockAtLeastFor = "30m")
  public void fetchEodTransactions() {
    log.info("Running SEB end-of-day transactions fetcher");
    for (BankAccountType account : BankAccountType.values()) {
      try {
        fetchEodTransactions(account);
      } catch (Exception exception) {
        log.error("SEB end-of-day transactions fetch failed: account={}", account, exception);
      }
    }
  }

  public void fetchEodTransactions(BankAccountType account) {
    String iban = sebAccountConfiguration.getAccountIban(account);
    log.info("Fetching SEB end-of-day transactions: account={}, iban={}", account, iban);

    String rawXml = sebGatewayClient.getEodTransactions(iban);
    persistBankingMessage(rawXml);
  }

  // @Scheduled(cron = "0 0 8 * * *", zone = "Europe/Tallinn")
  @SchedulerLock(
      name = "SebStatementFetcher_fetchLast7DaysTransactions",
      lockAtMostFor = "23h",
      lockAtLeastFor = "30m")
  public void fetchLast7DaysTransactions() {
    log.info("Running SEB last 7 days transactions fetcher");
    for (BankAccountType account : BankAccountType.values()) {
      try {
        fetchLast7DaysTransactions(account);
      } catch (Exception exception) {
        log.error("SEB last 7 days transactions fetch failed: account={}", account, exception);
      }
    }
  }

  public void fetchLast7DaysTransactions(BankAccountType account) {
    LocalDate today = LocalDate.now(clock());
    LocalDate sevenDaysAgo = today.minusDays(7);
    fetchHistoricTransactions(account, sevenDaysAgo, today);
  }

  public void fetchHistoricTransactions(
      BankAccountType account, LocalDate dateFrom, LocalDate dateTo) {
    String iban = sebAccountConfiguration.getAccountIban(account);
    log.info(
        "Fetching SEB historic transactions: account={}, iban={}, dateFrom={}, dateTo={}",
        account,
        iban,
        dateFrom,
        dateTo);

    String rawXml = sebGatewayClient.getTransactions(iban, dateFrom, dateTo);
    persistBankingMessage(rawXml);
  }

  private void persistBankingMessage(String rawXml) {
    String messageId = UUID.randomUUID().toString();
    BankingMessage message =
        BankingMessage.builder()
            .bankType(SEB)
            .requestId(messageId)
            .trackingId(messageId)
            .rawResponse(rawXml)
            .timezone(SEB_GATEWAY_TIME_ZONE.getId())
            .build();
    bankingMessageRepository.save(message);
    log.info("Persisted SEB banking message: id={}", message.getId());
  }
}
