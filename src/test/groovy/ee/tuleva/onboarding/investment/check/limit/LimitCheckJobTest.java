package ee.tuleva.onboarding.investment.check.limit;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.event.PipelineTracker;
import ee.tuleva.onboarding.investment.event.RunLimitCheckBackfillRequested;
import ee.tuleva.onboarding.investment.event.RunLimitCheckRequested;
import ee.tuleva.onboarding.investment.position.FeeAccrualPositionSyncJob;
import ee.tuleva.onboarding.savings.fund.nav.NavCalculationCompleted;
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
  void navCompletedDelegatesToServiceForSpecificFunds() {
    var funds = List.of(TUK75, TUK00);
    var results = List.of(mock(LimitCheckResult.class));
    when(limitCheckService.runChecksForFunds(funds)).thenReturn(results);

    job.onNavCalculationCompleted(new NavCalculationCompleted(funds));

    verify(limitCheckService).runChecksForFunds(funds);
    verify(limitCheckNotifier).notify(results);
  }

  @Test
  void adHocEventDelegatesToServiceAndNotifier() {
    var allFunds = List.of(TulevaFund.values());
    var results = List.of(mock(LimitCheckResult.class));
    when(limitCheckService.runChecksForFunds(allFunds)).thenReturn(results);

    job.onLimitCheckRequested(new RunLimitCheckRequested());

    verify(limitCheckService).runChecksForFunds(allFunds);
    verify(limitCheckNotifier).notify(results);
  }

  @Test
  void swallowsExceptions() {
    var funds = List.of(TUK75, TUK00);
    when(limitCheckService.runChecksForFunds(funds)).thenThrow(new RuntimeException("DB down"));

    job.onNavCalculationCompleted(new NavCalculationCompleted(funds));

    verify(limitCheckNotifier, never()).notify(any());
  }

  @Test
  void backfillSyncsFeeAccrualPositionsBeforeChecks() {
    var results = List.of(mock(LimitCheckResult.class));
    when(limitCheckService.backfillChecks(25)).thenReturn(results);

    job.backfillLimitChecks();

    var inOrder = inOrder(feeAccrualPositionSyncJob, limitCheckService);
    inOrder.verify(feeAccrualPositionSyncJob).sync(25);
    inOrder.verify(limitCheckService).backfillChecks(25);
    verify(limitCheckNotifier, never()).notify(any());
  }

  @Test
  void adHocBackfillEventTriggersBackfill() {
    var results = List.of(mock(LimitCheckResult.class));
    when(limitCheckService.backfillChecks(25)).thenReturn(results);

    job.onLimitCheckBackfillRequested(new RunLimitCheckBackfillRequested());

    var inOrder = inOrder(feeAccrualPositionSyncJob, limitCheckService);
    inOrder.verify(feeAccrualPositionSyncJob).sync(25);
    inOrder.verify(limitCheckService).backfillChecks(25);
  }

  @Test
  void partialFailureNotifiesBreachesAndMarksStepFailed() {
    var breachResult = mock(LimitCheckResult.class);
    when(breachResult.hasBreaches()).thenReturn(true);
    var partial = List.of(breachResult);

    var funds = List.of(TUK75, TUK00);
    when(limitCheckService.runChecksForFunds(funds))
        .thenThrow(new LimitCheckPartialFailureException("1 fund(s) failed", partial));

    job.onNavCalculationCompleted(new NavCalculationCompleted(funds));

    verify(limitCheckNotifier).notify(partial);
    verify(pipelineTracker).stepFailed(any(), eq("1 fund(s) failed"));
  }

  @Test
  void partialFailureWithoutBreachesSkipsNotification() {
    var okResult = mock(LimitCheckResult.class);
    when(okResult.hasBreaches()).thenReturn(false);
    var partial = List.of(okResult);

    var funds = List.of(TUK75, TUK00);
    when(limitCheckService.runChecksForFunds(funds))
        .thenThrow(new LimitCheckPartialFailureException("1 fund(s) failed", partial));

    job.onNavCalculationCompleted(new NavCalculationCompleted(funds));

    verify(limitCheckNotifier, never()).notify(any());
    verify(pipelineTracker).stepFailed(any(), eq("1 fund(s) failed"));
  }

  @Test
  void backfillSwallowsExceptions() {
    when(limitCheckService.backfillChecks(25)).thenThrow(new RuntimeException("DB down"));

    job.backfillLimitChecks();

    verify(limitCheckNotifier, never()).notify(any());
  }
}
