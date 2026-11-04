package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.JobRunSchedule.IMPORT_BUSINESS_HOURS;
import static ee.tuleva.onboarding.investment.JobRunSchedule.TIMEZONE;
import static java.util.Objects.requireNonNull;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import org.springframework.scheduling.support.CronExpression;

record SebReplayLease(Instant startedAt, Instant lockFreedAt) {

  private static final ZoneId SCHEDULE_ZONE = ZoneId.of(TIMEZONE);
  private static final CronExpression REPORT_IMPORTS = CronExpression.parse(IMPORT_BUSINESS_HOURS);
  private static final CronExpression LIVE_RECONCILIATION =
      CronExpression.parse(SebPendingTransactionReconciliationJob.SCHEDULE);
  private static final Duration AN_IMPORT_MAY_STILL_RUN_FOR = Duration.ofMinutes(15);
  private static final Duration LOCK_FREED_BEFORE_THE_NEXT_SCHEDULED_RUN = Duration.ofMinutes(5);
  private static final Duration ONE_REPORT_REPLAYS_WITHIN = Duration.ofMinutes(10);

  static Optional<SebReplayLease> startingAt(Instant now) {
    ZonedDateTime scheduleTime = now.atZone(SCHEDULE_ZONE);
    if (anImportStartedWithin(scheduleTime)) {
      return Optional.empty();
    }
    SebReplayLease lease =
        new SebReplayLease(
            now, nextScheduledRun(scheduleTime).minus(LOCK_FREED_BEFORE_THE_NEXT_SCHEDULED_RUN));
    return lease.isOver(now) ? Optional.empty() : Optional.of(lease);
  }

  Duration lockAtMostFor() {
    return Duration.between(startedAt, lockFreedAt);
  }

  boolean isOver(Instant now) {
    return !now.isBefore(lockFreedAt.minus(ONE_REPORT_REPLAYS_WITHIN));
  }

  private static boolean anImportStartedWithin(ZonedDateTime scheduleTime) {
    ZonedDateTime firstImportInTheWindow =
        requireNonNull(REPORT_IMPORTS.next(scheduleTime.minus(AN_IMPORT_MAY_STILL_RUN_FOR)));
    return !firstImportInTheWindow.isAfter(scheduleTime);
  }

  private static Instant nextScheduledRun(ZonedDateTime scheduleTime) {
    Instant nextImport = requireNonNull(REPORT_IMPORTS.next(scheduleTime)).toInstant();
    Instant nextLiveRun = requireNonNull(LIVE_RECONCILIATION.next(scheduleTime)).toInstant();
    return nextImport.isBefore(nextLiveRun) ? nextImport : nextLiveRun;
  }
}
