package ee.tuleva.onboarding.analytics.transaction.exchange.snapshot;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class ExchangeTransactionSnapshotScheduledJob {

  private final ExchangeTransactionSnapshotService exchangeTransactionSnapshotService;

  @Scheduled(cron = "0 45 3 * * SUN", zone = "Europe/Tallinn")
  @Transactional
  public void takeWeeklySnapshot() {
    log.info("Starting weekly exchange transaction snapshot job.");
    exchangeTransactionSnapshotService.takeSnapshot("WEEKLY");
  }

  @Scheduled(cron = "0 5 3 1 * ?", zone = "Europe/Tallinn")
  @Transactional
  public void takeMonthlySnapshot() {
    log.info("Starting monthly exchange transaction snapshot job.");
    exchangeTransactionSnapshotService.takeSnapshot("MONTHLY");
  }

  // TEMPORARY: One-time run to create missing snapshot for previous period
  // This creates a snapshot as if it was taken at 2025-08-01 00:01 (first minute of new period)
  // TO BE REMOVED after successful execution
  @Scheduled(cron = "0 0 20 5 8 ?", zone = "Europe/Tallinn") // Run at 19:35 on August 5, 2025
  @Transactional
  public void temporaryMissingPeriodSnapshot() {
    log.info("Starting TEMPORARY snapshot for previous period");

    // Create snapshot as if taken at the first minute of the new period
    LocalDateTime snapshotDateTime = LocalDateTime.of(2025, 8, 1, 0, 1);
    ZoneId estonianZone = ZoneId.of("Europe/Tallinn");
    OffsetDateTime snapshotTakenAt = snapshotDateTime.atZone(estonianZone).toOffsetDateTime();

    // Snapshot transactions from the previous period (reporting date = period start: April 1, 2025)
    LocalDate reportingDate = LocalDate.of(2025, 4, 1);

    exchangeTransactionSnapshotService.takeSnapshotForReportingDate(
        "PERIOD_FIX", snapshotTakenAt, reportingDate);

    log.info("TEMPORARY period snapshot completed - this method should be removed after execution");
  }
}
