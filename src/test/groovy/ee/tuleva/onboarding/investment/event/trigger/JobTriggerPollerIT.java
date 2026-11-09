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
}
