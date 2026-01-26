package ee.tuleva.onboarding.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

class SchedulerLockAnnotationTest {

  @Test
  @DisplayName("all @Scheduled methods should have @SchedulerLock annotation")
  void allScheduledMethodsShouldHaveSchedulerLock() throws Exception {
    var scheduledJobs =
        new Class<?>[] {
          ee.tuleva.onboarding.aml.ScheduledAmlCheckJob.class,
          ee.tuleva.onboarding.aml.health.AmlHealthThresholdCache.class,
          ee.tuleva.onboarding.aml.health.ScheduledAmlHealthCheckJob.class,
          ee.tuleva.onboarding.aml.risklevel.ScheduledAmlRiskMetadataRefreshJob.class,
          ee.tuleva.onboarding.aml.risklevel.ScheduledRiskLevelCheckJob.class,
          ee.tuleva.onboarding.analytics.transaction.exchange
              .ScheduledExchangeTransactionSynchronizationJob.class,
          ee.tuleva.onboarding.analytics.transaction.fund.ScheduledFundTransactionSynchronizationJob
              .class,
          ee.tuleva.onboarding.analytics.transaction.fundbalance
              .ScheduledFundBalanceSynchronizationJob.class,
          ee.tuleva.onboarding.analytics.transaction.thirdpillar
              .ScheduledThirdPillarTransactionSynchronizationJob.class,
          ee.tuleva.onboarding.analytics.transaction.unitowner.ScheduledUnitOwnerSynchronizationJob
              .class,
          ee.tuleva.onboarding.analytics.view.RefreshMaterializedViewsJob.class,
          ee.tuleva.onboarding.audit.health.ScheduledAuditHealthCheckJob.class,
          ee.tuleva.onboarding.capital.transfer.execution.CapitalTransferExecutionJob.class,
          ee.tuleva.onboarding.comparisons.fundvalue.FundValueIndexingJob.class,
          ee.tuleva.onboarding.comparisons.fundvalue.validation.FundValueIntegrityChecker.class,
          ee.tuleva.onboarding.fund.statistics.PensionFundStatisticsScheduledUpdate.class,
          ee.tuleva.onboarding.holdings.HoldingDetailsJob.class,
          ee.tuleva.onboarding.mandate.batch.poller.MandateBatchProcessingPoller.class,
          ee.tuleva.onboarding.notification.email.auto.AutoEmailSender.class,
          ee.tuleva.onboarding.savings.fund.CancellationJob.class,
          ee.tuleva.onboarding.savings.fund.PaymentReturningJob.class,
          ee.tuleva.onboarding.savings.fund.PaymentVerificationJob.class,
          ee.tuleva.onboarding.savings.fund.SavingsFundReservationJob.class,
          ee.tuleva.onboarding.savings.fund.issuing.FundAccountPaymentJob.class,
          ee.tuleva.onboarding.savings.fund.issuing.IssuingJob.class,
          ee.tuleva.onboarding.swedbank.fetcher.SwedbankMessageReceiver.class,
          ee.tuleva.onboarding.swedbank.fetcher.SwedbankStatementFetcher.class,
          ee.tuleva.onboarding.banking.processor.BankMessageProcessingScheduler.class,
        };

    var missingLockAnnotations = new StringBuilder();

    for (Class<?> jobClass : scheduledJobs) {
      for (Method method : jobClass.getDeclaredMethods()) {
        if (method.isAnnotationPresent(Scheduled.class)) {
          var schedulerLock = method.getAnnotation(SchedulerLock.class);
          if (schedulerLock == null) {
            missingLockAnnotations
                .append("\n  - ")
                .append(jobClass.getSimpleName())
                .append(".")
                .append(method.getName())
                .append("()");
          }
        }
      }
    }

    assertThat(missingLockAnnotations.toString())
        .as("All @Scheduled methods must have @SchedulerLock annotation")
        .isEmpty();
  }

  @Test
  @DisplayName("@SchedulerLock annotations should have reasonable lock times")
  void schedulerLockAnnotationsShouldHaveReasonableLockTimes() throws Exception {
    var scheduledJobs =
        new Class<?>[] {
          ee.tuleva.onboarding.aml.ScheduledAmlCheckJob.class,
          ee.tuleva.onboarding.mandate.batch.poller.MandateBatchProcessingPoller.class,
          ee.tuleva.onboarding.savings.fund.issuing.IssuingJob.class,
        };

    for (Class<?> jobClass : scheduledJobs) {
      for (Method method : jobClass.getDeclaredMethods()) {
        if (method.isAnnotationPresent(SchedulerLock.class)) {
          var schedulerLock = method.getAnnotation(SchedulerLock.class);

          var lockAtMostFor = parseDuration(schedulerLock.lockAtMostFor());
          var lockAtLeastFor = parseDuration(schedulerLock.lockAtLeastFor());

          assertThat(lockAtMostFor)
              .as(
                  "lockAtMostFor should be positive for %s.%s",
                  jobClass.getSimpleName(), method.getName())
              .isPositive();

          assertThat(lockAtLeastFor)
              .as(
                  "lockAtLeastFor should be positive for %s.%s",
                  jobClass.getSimpleName(), method.getName())
              .isPositive();

          assertThat(lockAtMostFor)
              .as(
                  "lockAtMostFor should be greater than lockAtLeastFor for %s.%s",
                  jobClass.getSimpleName(), method.getName())
              .isGreaterThan(lockAtLeastFor);

          assertThat(schedulerLock.name())
              .as(
                  "Lock name should not be empty for %s.%s",
                  jobClass.getSimpleName(), method.getName())
              .isNotEmpty();
        }
      }
    }
  }

  private long parseDuration(String duration) {
    // Parse simple duration strings like "10s", "5m", "1h"
    if (duration.endsWith("ms")) {
      return Long.parseLong(duration.substring(0, duration.length() - 2));
    } else if (duration.endsWith("s")) {
      return Long.parseLong(duration.substring(0, duration.length() - 1)) * 1000;
    } else if (duration.endsWith("m")) {
      return Long.parseLong(duration.substring(0, duration.length() - 1)) * 60 * 1000;
    } else if (duration.endsWith("h")) {
      return Long.parseLong(duration.substring(0, duration.length() - 1)) * 60 * 60 * 1000;
    }
    throw new IllegalArgumentException("Cannot parse duration: " + duration);
  }
}
