package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.JobRunSchedule.TIMEZONE;
import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportType.PENDING_TRANSACTIONS;

import ee.tuleva.onboarding.investment.report.InvestmentReport;
import ee.tuleva.onboarding.investment.report.InvestmentReportService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile({"production", "staging"})
@RequiredArgsConstructor
class SebPendingTransactionReconciliationJob {

  private static final int LOOKBACK_DAYS = 7;

  private final Clock clock;
  private final InvestmentReportService reportService;
  private final SebPendingTransactionReconciliationService reconciliationService;

  @Scheduled(cron = "0 0 9 * * *", zone = TIMEZONE)
  @SchedulerLock(
      name = "SebPendingTransactionReconciliationJob",
      lockAtMostFor = "30m",
      lockAtLeastFor = "1m")
  public void run() {
    LocalDate today = LocalDate.now(clock);
    log.info(
        "Running scheduled SEB pending transactions reconciliation: today={}, lookbackDays={}",
        today,
        LOOKBACK_DAYS);
    for (int i = 0; i <= LOOKBACK_DAYS; i++) {
      LocalDate date = today.minusDays(i);
      try {
        Optional<InvestmentReport> report =
            reportService.getReport(SEB, PENDING_TRANSACTIONS, date);
        if (report.isEmpty()) {
          continue;
        }
        reconciliationService.reconcile(report.get());
      } catch (Exception e) {
        log.error(
            "SEB pending transactions reconciliation failed, continuing: reportDate={}", date, e);
      }
    }
  }
}
