package ee.tuleva.onboarding.banking.seb.fetcher;

import static ee.tuleva.onboarding.banking.seb.Seb.SEB_GATEWAY_TIME_ZONE;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;

import ee.tuleva.onboarding.banking.BankAccount;
import ee.tuleva.onboarding.banking.BankAccounts;
import ee.tuleva.onboarding.banking.event.BankMessageEvents.FetchSebCurrentDayTransactionsRequested;
import ee.tuleva.onboarding.banking.event.BankMessageEvents.FetchSebEodTransactionsRequested;
import ee.tuleva.onboarding.banking.event.BankMessageEvents.FetchSebHistoricTransactionsRequested;
import ee.tuleva.onboarding.banking.statement.StatementPeriod;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

@RequiredArgsConstructor
@Slf4j
public class SebStatementFetchingScheduler {

  static final String END_OF_DAY_FETCH_CRON = "0 0/30 4-23 * * *";
  static final String GAP_REPORT_CRON = "0 10 9 * * *";
  private static final int CATCH_UP_DAYS = 7;

  private final ApplicationEventPublisher eventPublisher;
  private final BankAccounts bankAccounts;
  private final StatementCoverage statementCoverage;
  private final Clock clock;

  @Scheduled(cron = "0 */5 9-17 * * MON-FRI", zone = "Europe/Tallinn")
  @SchedulerLock(
      name = "SebStatementFetchingScheduler_fetchCurrentDayTransactions",
      lockAtMostFor = "23h",
      lockAtLeastFor = "30m")
  public void fetchCurrentDayTransactions() {
    log.info("Running SEB current day transactions fetching scheduler");
    for (BankAccount account : bankAccounts.findAll(TKF100)) {
      try {
        eventPublisher.publishEvent(new FetchSebCurrentDayTransactionsRequested(account));
      } catch (Exception exception) {
        log.error("SEB current day transactions fetch failed: account={}", account, exception);
        if (isGatewayUnavailable(exception)) {
          return;
        }
      }
    }
  }

  @Scheduled(cron = END_OF_DAY_FETCH_CRON, zone = "Europe/Tallinn")
  @SchedulerLock(
      name = "SebStatementFetchingScheduler_fetchEodTransactions",
      lockAtMostFor = "2h",
      lockAtLeastFor = "1m")
  public void fetchEodTransactions() {
    log.info("Running SEB end-of-day statement fetching scheduler");
    var yesterday = yesterday();
    for (BankAccount account : bankAccounts.findAll()) {
      if (!fetchEndOfDayStatement(account, yesterday)
          || !catchUpMissedStatements(account, yesterday)) {
        log.warn("SEB gateway unavailable, stopping this run: account={}", account);
        return;
      }
    }
  }

  @Scheduled(cron = GAP_REPORT_CRON, zone = "Europe/Tallinn")
  @SchedulerLock(
      name = "SebStatementFetchingScheduler_reportMissingStatements",
      lockAtMostFor = "10m",
      lockAtLeastFor = "1m")
  public void reportMissingStatements() {
    var yesterday = yesterday();
    var gaps =
        bankAccounts.findAll().stream()
            .flatMap(
                account ->
                    statementCoverage
                        .unprocessedPeriods(account, yesterday.minusDays(CATCH_UP_DAYS), yesterday)
                        .stream()
                        .map(period -> new StatementGap(account, period)))
            .toList();
    if (gaps.isEmpty()) {
      return;
    }
    log.error("SEB statements not booked in the ledger: gaps={}", gaps);
    eventPublisher.publishEvent(new SebStatementGapsFound(gaps));
  }

  private boolean fetchEndOfDayStatement(BankAccount account, LocalDate statementDate) {
    if (statementCoverage.isReceived(account, statementDate)) {
      return true;
    }
    return publishRetryingNextRun(
        new FetchSebEodTransactionsRequested(account, statementDate),
        "SEB end-of-day statement fetch failed, retrying on the next run: account=%s, statementDate=%s"
            .formatted(account, statementDate));
  }

  private boolean catchUpMissedStatements(BankAccount account, LocalDate yesterday) {
    var gaps =
        statementCoverage.missingPeriods(
            account, yesterday.minusDays(CATCH_UP_DAYS), yesterday.minusDays(1));
    for (StatementPeriod gap : gaps) {
      var published =
          publishRetryingNextRun(
              new FetchSebHistoricTransactionsRequested(account, gap.from(), gap.to()),
              "SEB statement catch-up fetch failed, retrying on the next run: account=%s, from=%s, to=%s"
                  .formatted(account, gap.from(), gap.to()));
      if (!published) {
        return false;
      }
    }
    return true;
  }

  private boolean publishRetryingNextRun(Object event, String failureMessage) {
    try {
      eventPublisher.publishEvent(event);
      return true;
    } catch (Exception exception) {
      log.warn(failureMessage, exception);
      return !isGatewayUnavailable(exception);
    }
  }

  private static boolean isGatewayUnavailable(Exception exception) {
    return exception instanceof HttpServerErrorException
        || exception instanceof ResourceAccessException;
  }

  private LocalDate yesterday() {
    return clock.instant().atZone(SEB_GATEWAY_TIME_ZONE).toLocalDate().minusDays(1);
  }
}
