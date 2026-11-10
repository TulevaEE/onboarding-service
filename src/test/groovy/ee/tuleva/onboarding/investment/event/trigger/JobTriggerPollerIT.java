package ee.tuleva.onboarding.investment.event.trigger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.BDDMockito.then;
import static org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED;

import ee.tuleva.onboarding.investment.event.JobBusyException;
import ee.tuleva.onboarding.investment.event.RunReportImportRequested;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.time.ClockHolder;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@Import(JobTriggerQueue.class)
@Transactional(propagation = NOT_SUPPORTED)
@ExtendWith(MockitoExtension.class)
class JobTriggerPollerIT {

  private static final Instant NOW = Instant.parse("2026-05-13T09:00:00Z");
  private static final String IMPORT = "ReportImportJob";
  private static final String RECONCILIATION =
      "SebPendingTransactionReportReconciliationJob:2026-05-13";
  private static final String LIMIT_CHECK = "LimitCheckJob";

  @Autowired JobTriggerRepository repository;
  @Autowired JobTriggerQueue queue;
  @Mock OperationsNotificationService notificationService;

  @BeforeEach
  void fixTheClockOnAnEmptyQueue() {
    ClockHolder.setClock(Clock.fixed(NOW, ZoneOffset.UTC));
    repository.deleteAll();
  }

  @AfterEach
  void emptyTheQueueAndRestoreTheClock() {
    repository.deleteAll();
    ClockHolder.setDefaultClock();
  }

  @Test
  void aTriggerAnEarlierJobOfTheSamePollDefersAgainKeepsItsRestartedBudgetWhileItsJobIsBusy() {
    repository.save(
        JobTrigger.builder().jobName(IMPORT).createdAt(NOW.minus(Duration.ofHours(7))).build());
    repository.save(
        JobTrigger.builder()
            .jobName(RECONCILIATION)
            .createdAt(NOW.minus(Duration.ofHours(6)))
            .build());
    ApplicationEventPublisher importDefersTheReconciliationWhoseLockStaysHeld =
        event -> {
          if (event instanceof RunReportImportRequested) {
            queue.defer(RECONCILIATION);
            return;
          }
          throw new JobBusyException("reconciliation lock held");
        };
    JobTriggerPoller poller =
        new JobTriggerPoller(
            repository,
            importDefersTheReconciliationWhoseLockStaysHeld,
            notificationService,
            new JobTriggerLease());

    poller.poll();

    assertThat(repository.findAll())
        .extracting(JobTrigger::getJobName, JobTrigger::getStatus, JobTrigger::getCreatedAt)
        .containsExactlyInAnyOrder(
            tuple(IMPORT, "COMPLETED", NOW.minus(Duration.ofHours(7))),
            tuple(RECONCILIATION, "PENDING", NOW));
    then(notificationService).shouldHaveNoInteractions();
  }

  @Test
  void aTriggerWhoseQueuedTimeMovesWhileItsJobRunsIsJudgedByThatTimeWhenTheJobIsBusy() {
    JobTrigger queued =
        repository.save(
            JobTrigger.builder()
                .jobName(RECONCILIATION)
                .createdAt(NOW.minus(Duration.ofHours(6)))
                .build());
    ApplicationEventPublisher requeuedWhileItRunsAndStillBusy =
        event -> {
          JobTrigger row = repository.findById(queued.getId()).orElseThrow();
          row.setCreatedAt(NOW);
          repository.save(row);
          throw new JobBusyException("reconciliation lock held");
        };

    pollerPublishingTo(requeuedWhileItRunsAndStillBusy).poll();

    assertThat(repository.findAll())
        .extracting(JobTrigger::getJobName, JobTrigger::getStatus, JobTrigger::getCreatedAt)
        .containsExactly(tuple(RECONCILIATION, "PENDING", NOW));
    then(notificationService).shouldHaveNoInteractions();
  }

  @Test
  void aDeferralWhileItsTriggersJobRunsQueuesAnotherTriggerThatTheCompletionDoesNotAbsorb() {
    repository.save(
        JobTrigger.builder()
            .jobName(RECONCILIATION)
            .createdAt(NOW.minus(Duration.ofHours(1)))
            .build());
    ApplicationEventPublisher theSameReportArrivesAgainWhileItsRunStarts =
        event -> queue.defer(RECONCILIATION);

    pollerPublishingTo(theSameReportArrivesAgainWhileItsRunStarts).poll();

    assertThat(repository.findAll())
        .extracting(JobTrigger::getJobName, JobTrigger::getStatus, JobTrigger::getCreatedAt)
        .containsExactlyInAnyOrder(
            tuple(RECONCILIATION, "COMPLETED", NOW.minus(Duration.ofHours(1))),
            tuple(RECONCILIATION, "PENDING", NOW));
  }

  @Test
  void aTriggerStoppedByHandWhileItsJobRunsKeepsThatStateAndTheNextTriggerStillRuns() {
    JobTrigger stoppedByHand =
        repository.save(
            JobTrigger.builder().jobName(IMPORT).createdAt(NOW.minus(Duration.ofHours(2))).build());
    repository.save(
        JobTrigger.builder()
            .jobName(LIMIT_CHECK)
            .createdAt(NOW.minus(Duration.ofHours(1)))
            .build());
    ApplicationEventPublisher importStoppedByHandWhileItRuns =
        event -> {
          if (event instanceof RunReportImportRequested) {
            JobTrigger row = repository.findById(stoppedByHand.getId()).orElseThrow();
            row.setStatus("FAILED");
            row.setErrorMessage("stopped by hand");
            repository.save(row);
          }
        };

    pollerPublishingTo(importStoppedByHandWhileItRuns).poll();

    assertThat(repository.findAll())
        .extracting(JobTrigger::getJobName, JobTrigger::getStatus, JobTrigger::getErrorMessage)
        .containsExactlyInAnyOrder(
            tuple(IMPORT, "FAILED", "stopped by hand"), tuple(LIMIT_CHECK, "COMPLETED", null));
  }

  @Test
  void aClaimStampsOnlyTheFirstStartSoABusyRetryKeepsIt() {
    Instant firstStart = NOW.minus(Duration.ofMinutes(30));
    repository.save(
        JobTrigger.builder()
            .jobName(RECONCILIATION)
            .createdAt(NOW.minus(Duration.ofMinutes(31)))
            .startedAt(firstStart)
            .build());
    repository.save(
        JobTrigger.builder().jobName(IMPORT).createdAt(NOW.minus(Duration.ofMinutes(1))).build());
    ApplicationEventPublisher everyJobIsBusy =
        event -> {
          throw new JobBusyException("lock held");
        };

    pollerPublishingTo(everyJobIsBusy).poll();

    assertThat(repository.findAll())
        .extracting(JobTrigger::getJobName, JobTrigger::getStatus, JobTrigger::getStartedAt)
        .containsExactlyInAnyOrder(
            tuple(RECONCILIATION, "PENDING", firstStart), tuple(IMPORT, "PENDING", NOW));
  }

  private JobTriggerPoller pollerPublishingTo(ApplicationEventPublisher eventPublisher) {
    return new JobTriggerPoller(
        repository, eventPublisher, notificationService, new JobTriggerLease());
  }
}
