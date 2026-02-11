package ee.tuleva.onboarding.banking.seb.fetcher;

import ee.tuleva.onboarding.banking.BankAccountType;
import ee.tuleva.onboarding.banking.event.BankMessageEvents.FetchSebCurrentDayTransactionsRequested;
import ee.tuleva.onboarding.banking.event.BankMessageEvents.FetchSebEodTransactionsRequested;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;

@RequiredArgsConstructor
@Slf4j
public class SebStatementFetchingScheduler {

  private final ApplicationEventPublisher eventPublisher;

  @Scheduled(cron = "0 */5 9-17 * * MON-FRI", zone = "Europe/Tallinn")
  @SchedulerLock(
      name = "SebStatementFetchingScheduler_fetchCurrentDayTransactions",
      lockAtMostFor = "23h",
      lockAtLeastFor = "30m")
  public void fetchCurrentDayTransactions() {
    log.info("Running SEB current day transactions fetching scheduler");
    for (BankAccountType account : BankAccountType.values()) {
      try {
        eventPublisher.publishEvent(new FetchSebCurrentDayTransactionsRequested(account));
      } catch (Exception exception) {
        log.error("SEB current day transactions fetch failed: account={}", account, exception);
      }
    }
  }

  @Scheduled(cron = "0 0 1 * * *", zone = "Europe/Tallinn")
  @SchedulerLock(
      name = "SebStatementFetchingScheduler_fetchEodTransactions",
      lockAtMostFor = "23h",
      lockAtLeastFor = "30m")
  public void fetchEodTransactions() {
    log.info("Running SEB end-of-day transactions fetching scheduler");
    for (BankAccountType account : BankAccountType.values()) {
      try {
        eventPublisher.publishEvent(new FetchSebEodTransactionsRequested(account));
      } catch (Exception exception) {
        log.error("SEB end-of-day transactions fetch failed: account={}", account, exception);
      }
    }
  }
}
