package ee.tuleva.onboarding.analytics.exchange;

import ee.tuleva.onboarding.deadline.MandateDeadlinesService;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@Profile("!dev")
public class ScheduledExchangeTransactionSynchronizationJob {

  private final ExchangeTransactionSynchronizer exchangeTransactionSynchronizer;
  private final MandateDeadlinesService mandateDeadlinesService;

  @Scheduled(cron = "0 0 2 * * ?", zone = "Europe/Tallinn")
  public void run() {
    log.info("Starting exchange transactions synchronization job");
    LocalDate startDate = mandateDeadlinesService.getCurrentPeriodStartDate();
    exchangeTransactionSynchronizer.syncTransactions(
        startDate, Optional.empty(), Optional.empty(), false);
    log.info("Transactions exchange synchronization job completed");
  }

  @Scheduled(cron = "0 05 11 19 3 ?", zone = "Europe/Tallinn")
  public void runInitialTransactionsSync() {
    log.info("Starting initial exchange transactions synchronization job");
    LocalDate startDate = LocalDate.of(2024, 12, 1);
    exchangeTransactionSynchronizer.syncTransactions(
        startDate, Optional.empty(), Optional.empty(), false);
    log.info("Finished initial exchange transactions synchronization job completed");
  }
}
