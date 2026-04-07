package ee.tuleva.onboarding.investment.check.limit;

import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.investment.event.FeeAccrualPositionsSynced;
import ee.tuleva.onboarding.investment.event.RunLimitCheckRequested;
import ee.tuleva.onboarding.investment.position.FeeAccrualPositionSyncJob;
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
  @InjectMocks LimitCheckJob job;

  @Test
  void chainEventDelegatesToServiceAndNotifier() {
    var results = List.of(mock(LimitCheckResult.class));
    when(limitCheckService.runChecks()).thenReturn(results);

    job.onFeeAccrualPositionsSynced(new FeeAccrualPositionsSynced());

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
  void chainEventSwallowsExceptions() {
    when(limitCheckService.runChecks()).thenThrow(new RuntimeException("DB down"));

    job.onFeeAccrualPositionsSynced(new FeeAccrualPositionsSynced());

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
  void backfillSwallowsExceptions() {
    when(limitCheckService.backfillChecks(10)).thenThrow(new RuntimeException("DB down"));

    job.backfillLimitChecks();

    verify(limitCheckNotifier, never()).notify(any());
  }
}
