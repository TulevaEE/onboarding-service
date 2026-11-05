package ee.tuleva.onboarding.investment.event.trigger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.time.ClockHolder;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
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
  private static final List<String> WAITING_OR_RUNNING = List.of("PENDING", "PROCESSING");

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
    given(repository.findFirstByJobNameAndStatusIn(REPORT_TRIGGER, WAITING_OR_RUNNING))
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
    given(repository.findFirstByJobNameAndStatusIn(REPORT_TRIGGER, WAITING_OR_RUNNING))
        .willReturn(Optional.of(waiting));

    queue.defer(REPORT_TRIGGER);

    assertThat(waiting.getCreatedAt()).isEqualTo(NOW);
    verify(repository).save(waiting);
  }

  @Test
  void aDeferralWhileTheSameTriggerIsRunningQueuesNothing() {
    JobTrigger running =
        JobTrigger.builder()
            .jobName(REPORT_TRIGGER)
            .status("PROCESSING")
            .createdAt(NOW.minus(Duration.ofMinutes(1)))
            .build();
    given(repository.findFirstByJobNameAndStatusIn(REPORT_TRIGGER, WAITING_OR_RUNNING))
        .willReturn(Optional.of(running));

    queue.defer(REPORT_TRIGGER);

    verify(repository, never()).save(any());
  }

  @Test
  void aDeferralForAnotherReportDateQueuesItsOwnTriggerBesideAWaitingOne() {
    String otherDate = "SebPendingTransactionReportReconciliationJob:2026-05-12";
    given(repository.findFirstByJobNameAndStatusIn(otherDate, WAITING_OR_RUNNING))
        .willReturn(Optional.empty());

    queue.defer(otherDate);

    verify(repository)
        .save(argThat((JobTrigger trigger) -> otherDate.equals(trigger.getJobName())));
  }
}
