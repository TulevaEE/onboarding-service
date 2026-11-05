package ee.tuleva.onboarding.investment.event.trigger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.time.ClockHolder;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JobTriggerQueueTest {

  private static final Instant NOW = Instant.parse("2026-05-13T09:00:00Z");
  private static final String REPORT_TRIGGER =
      "SebPendingTransactionReportReconciliationJob:2026-05-13";

  @Mock JobTriggerRepository repository;

  @InjectMocks JobTriggerQueue queue;

  @BeforeEach
  void fixTheClock() {
    ClockHolder.setClock(Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @AfterEach
  void restoreTheClock() {
    ClockHolder.setDefaultClock();
  }

  @Test
  void aDeferralQueuesATriggerTheNextPollRuns() {
    given(repository.findFirstByJobNameAndStatus(REPORT_TRIGGER, "PENDING"))
        .willReturn(Optional.empty());

    queue.defer(REPORT_TRIGGER);

    verify(repository)
        .save(
            argThat(
                (JobTrigger trigger) ->
                    REPORT_TRIGGER.equals(trigger.getJobName())
                        && "PENDING".equals(trigger.getStatus())));
  }

  @Test
  void aRedeferralRestartsTheWaitingTriggersBudgetFromTheLatestDeferral() {
    JobTrigger waiting =
        JobTrigger.builder()
            .jobName(REPORT_TRIGGER)
            .status("PENDING")
            .createdAt(NOW.minus(Duration.ofHours(5)))
            .build();
    given(repository.findFirstByJobNameAndStatus(REPORT_TRIGGER, "PENDING"))
        .willReturn(Optional.of(waiting));

    queue.defer(REPORT_TRIGGER);

    assertThat(waiting.getCreatedAt()).isEqualTo(NOW);
    verify(repository).save(waiting);
  }

  @Test
  void aDeferralForAnotherReportDateQueuesItsOwnTriggerBesideAWaitingOne() {
    String otherDate = "SebPendingTransactionReportReconciliationJob:2026-05-12";
    given(repository.findFirstByJobNameAndStatus(otherDate, "PENDING"))
        .willReturn(Optional.empty());

    queue.defer(otherDate);

    verify(repository)
        .save(argThat((JobTrigger trigger) -> otherDate.equals(trigger.getJobName())));
  }
}
