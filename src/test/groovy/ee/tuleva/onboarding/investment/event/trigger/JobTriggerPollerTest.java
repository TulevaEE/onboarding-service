package ee.tuleva.onboarding.investment.event.trigger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;

import ee.tuleva.onboarding.investment.event.RunLimitCheckRequested;
import ee.tuleva.onboarding.investment.event.RunTrackingDifferenceBackfillRequested;
import ee.tuleva.onboarding.investment.event.RunTrackingDifferenceCheckRequested;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class JobTriggerPollerTest {

  @Mock JobTriggerRepository repository;
  @Mock ApplicationEventPublisher eventPublisher;

  @InjectMocks JobTriggerPoller poller;

  @Test
  void publishesEventForPendingTrigger() {
    var trigger = JobTrigger.builder().jobName("TrackingDifferenceJob").status("PENDING").build();
    given(repository.findByStatusOrderByCreatedAtAsc("PENDING")).willReturn(List.of(trigger));

    poller.poll();

    then(eventPublisher).should().publishEvent(any(RunTrackingDifferenceCheckRequested.class));
    assertThat(trigger.getStatus()).isEqualTo("COMPLETED");
  }

  @Test
  void publishesCorrectEventType() {
    var trigger = JobTrigger.builder().jobName("LimitCheckJob").status("PENDING").build();
    given(repository.findByStatusOrderByCreatedAtAsc("PENDING")).willReturn(List.of(trigger));

    poller.poll();

    var captor = ArgumentCaptor.forClass(Object.class);
    then(eventPublisher).should().publishEvent(captor.capture());
    assertThat(captor.getValue()).isInstanceOf(RunLimitCheckRequested.class);
  }

  @Test
  void marksFailedForUnknownJob() {
    var trigger = JobTrigger.builder().jobName("NonExistentJob").status("PENDING").build();
    given(repository.findByStatusOrderByCreatedAtAsc("PENDING")).willReturn(List.of(trigger));

    poller.poll();

    assertThat(trigger.getStatus()).isEqualTo("FAILED");
    assertThat(trigger.getErrorMessage()).contains("Unknown job");
    then(eventPublisher).should(never()).publishEvent(any());
  }

  @Test
  void marksFailedWhenEventListenerThrows() {
    var trigger = JobTrigger.builder().jobName("TrackingDifferenceJob").status("PENDING").build();
    given(repository.findByStatusOrderByCreatedAtAsc("PENDING")).willReturn(List.of(trigger));
    doThrow(new RuntimeException("boom"))
        .when(eventPublisher)
        .publishEvent(any(RunTrackingDifferenceCheckRequested.class));

    poller.poll();

    assertThat(trigger.getStatus()).isEqualTo("FAILED");
    assertThat(trigger.getErrorMessage()).isEqualTo("boom");
  }

  @Test
  void skipsWhenNoPendingTriggers() {
    given(repository.findByStatusOrderByCreatedAtAsc("PENDING")).willReturn(List.of());

    poller.poll();

    then(eventPublisher).shouldHaveNoInteractions();
  }

  @Test
  void publishesBackfillEventForTrackingDifferenceBackfillJob() {
    var trigger =
        JobTrigger.builder().jobName("TrackingDifferenceBackfillJob").status("PENDING").build();
    given(repository.findByStatusOrderByCreatedAtAsc("PENDING")).willReturn(List.of(trigger));

    poller.poll();

    var captor = ArgumentCaptor.forClass(Object.class);
    then(eventPublisher).should().publishEvent(captor.capture());
    assertThat(captor.getValue()).isInstanceOf(RunTrackingDifferenceBackfillRequested.class);
  }

  @Test
  void processesMultipleTriggers() {
    var trigger1 = JobTrigger.builder().jobName("TrackingDifferenceJob").status("PENDING").build();
    var trigger2 = JobTrigger.builder().jobName("LimitCheckJob").status("PENDING").build();
    given(repository.findByStatusOrderByCreatedAtAsc("PENDING"))
        .willReturn(List.of(trigger1, trigger2));

    poller.poll();

    assertThat(trigger1.getStatus()).isEqualTo("COMPLETED");
    assertThat(trigger2.getStatus()).isEqualTo("COMPLETED");
  }
}
