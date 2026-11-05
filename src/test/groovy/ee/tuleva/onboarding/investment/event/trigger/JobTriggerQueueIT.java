package ee.tuleva.onboarding.investment.event.trigger;

import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.time.ClockHolder;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(JobTriggerQueue.class)
class JobTriggerQueueIT {

  private static final Instant NOW = Instant.parse("2026-05-13T09:00:00Z");
  private static final String REPORT_TRIGGER =
      "SebPendingTransactionReportReconciliationJob:2026-05-13";

  @Autowired JobTriggerRepository repository;
  @Autowired JobTriggerQueue queue;

  @BeforeEach
  void fixTheClock() {
    ClockHolder.setClock(Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @AfterEach
  void restoreTheClock() {
    ClockHolder.setDefaultClock();
  }

  @Test
  void aDeferralWhileTheSameTriggerIsProcessingQueuesAPendingOneBesideIt() {
    repository.save(
        JobTrigger.builder()
            .jobName(REPORT_TRIGGER)
            .status("PROCESSING")
            .createdAt(NOW.minus(Duration.ofMinutes(1)))
            .startedAt(NOW.minus(Duration.ofMinutes(1)))
            .build());

    queue.defer(REPORT_TRIGGER);

    assertThat(repository.findAll())
        .filteredOn(trigger -> REPORT_TRIGGER.equals(trigger.getJobName()))
        .extracting(JobTrigger::getStatus)
        .containsExactlyInAnyOrder("PROCESSING", "PENDING");
  }

  @Test
  void aDeferralWhileTheSameTriggerIsPendingRestartsThatOnesBudgetInsteadOfQueuingAnother() {
    repository.save(
        JobTrigger.builder()
            .jobName(REPORT_TRIGGER)
            .status("PENDING")
            .createdAt(NOW.minus(Duration.ofHours(5)))
            .build());

    queue.defer(REPORT_TRIGGER);

    assertThat(repository.findAll())
        .filteredOn(trigger -> REPORT_TRIGGER.equals(trigger.getJobName()))
        .singleElement()
        .extracting(JobTrigger::getStatus, JobTrigger::getCreatedAt)
        .containsExactly("PENDING", NOW);
  }
}
