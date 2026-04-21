package ee.tuleva.onboarding.investment.check.limit;

import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.investment.event.PipelineTracker;
import ee.tuleva.onboarding.investment.event.RunLimitCheckBackfillRequested;
import ee.tuleva.onboarding.investment.event.RunLimitCheckRequested;
import ee.tuleva.onboarding.investment.position.FeeAccrualPositionSyncJob;
import ee.tuleva.onboarding.savings.fund.nav.AllNavCalculationsCompleted;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LimitCheckJobTest {

  @Mock LimitCheckService limitCheckService;
  @Mock LimitCheckNotifier limitCheckNotifier;
  @Mock FeeAccrualPositionSyncJob feeAccrualPositionSyncJob;
  @Mock PipelineTracker pipelineTracker;
  @InjectMocks LimitCheckJob job;

  @Test
  void navCompletedDelegatesToServiceAndNotifier() {
    var results = List.of(mock(LimitCheckResult.class));
    when(limitCheckService.runChecks()).thenReturn(results);

    job.onAllNavCalculationsCompleted(new AllNavCalculationsCompleted());

    verify(limitCheckService).runChecks();
    verify(limitCheckNotifier).notify(results);
  }

  @Test
  void adHocEventDelegatesToServiceAndNotifier() {
    var results = List.of(mock(LimitCheckResult.class));
    when(limitCheckService.runChecks()).thenReturn(results);

    job.onLimitCheckRequested(new RunLimitCheckRequested());

    verify(limitCheckService).runChecks();
    verify(limitCheckNotifier).notify(results);
  }

  @Test
  void swallowsExceptions() {
    when(limitCheckService.runChecks()).thenThrow(new RuntimeException("DB down"));

    job.onAllNavCalculationsCompleted(new AllNavCalculationsCompleted());

    verify(limitCheckNotifier, never()).notify(any());
  }

  @Test
  void backfillSyncsFeeAccrualPositionsBeforeChecks() {
    var results = List.of(mock(LimitCheckResult.class));
    when(limitCheckService.backfillChecks(10)).thenReturn(results);

    job.backfillLimitChecks();

    var inOrder = inOrder(feeAccrualPositionSyncJob, limitCheckService);
    inOrder.verify(feeAccrualPositionSyncJob).sync(10);
    inOrder.verify(limitCheckService).backfillChecks(10);
    verify(limitCheckNotifier, never()).notify(any());
  }

  @Test
  void adHocBackfillEventTriggersBackfill() {
    var results = List.of(mock(LimitCheckResult.class));
    when(limitCheckService.backfillChecks(10)).thenReturn(results);

    job.onLimitCheckBackfillRequested(new RunLimitCheckBackfillRequested());

    var inOrder = inOrder(feeAccrualPositionSyncJob, limitCheckService);
    inOrder.verify(feeAccrualPositionSyncJob).sync(10);
    inOrder.verify(limitCheckService).backfillChecks(10);
  }

  @Test
  void backfillSwallowsExceptions() {
    when(limitCheckService.backfillChecks(10)).thenThrow(new RuntimeException("DB down"));

    job.backfillLimitChecks();

    verify(limitCheckNotifier, never()).notify(any());
  }
}
