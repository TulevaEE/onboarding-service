package ee.tuleva.onboarding.investment.event.trigger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JobTriggerQueueTest {

  @Mock JobTriggerRepository repository;

  @InjectMocks JobTriggerQueue queue;

  @Test
  void queuesATriggerTheNextPollRuns() {
    given(repository.existsByJobNameAndStatus("SebPendingTransactionReconciliationJob", "PENDING"))
        .willReturn(false);

    queue.enqueueUnlessPending("SebPendingTransactionReconciliationJob");

    verify(repository)
        .save(
            argThat(
                (JobTrigger trigger) ->
                    "SebPendingTransactionReconciliationJob".equals(trigger.getJobName())
                        && "PENDING".equals(trigger.getStatus())));
  }

  @Test
  void queuesNothingWhileTheSameJobIsAlreadyWaiting() {
    given(repository.existsByJobNameAndStatus("SebPendingTransactionReconciliationJob", "PENDING"))
        .willReturn(true);

    queue.enqueueUnlessPending("SebPendingTransactionReconciliationJob");

    verify(repository, never()).save(any());
  }
}
