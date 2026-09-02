package ee.tuleva.onboarding.investment.check.limit;

import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.investment.event.RunLimitCheckBackfillRequested;
import ee.tuleva.onboarding.investment.event.RunLimitCheckRequested;
import ee.tuleva.onboarding.investment.position.FeeAccrualPositionSyncJob;
import ee.tuleva.onboarding.pipeline.PipelineTracker;
import ee.tuleva.onboarding.savings.NavCalculationCompleted;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
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
    when(limitCheckService.runChecksForFunds(funds)).thenReturn(LimitCheckRun.of(results));

    job.onNavCalculationCompleted(new NavCalculationCompleted(funds));

    verify(limitCheckService).runChecksForFunds(funds);
    verify(limitCheckNotifier).notify(LimitCheckRun.of(results));
  }

  @Test
  void adHocEventDelegatesToServiceAndNotifier() {
    var allFunds = List.of(TulevaFund.values());
    var results = List.of(mock(LimitCheckResult.class));
    when(limitCheckService.runChecksForFunds(allFunds)).thenReturn(LimitCheckRun.of(results));

    job.onLimitCheckRequested(new RunLimitCheckRequested());

    verify(limitCheckService).runChecksForFunds(allFunds);
    verify(limitCheckNotifier).notify(LimitCheckRun.of(results));
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
    var partial = new LimitCheckRun(List.of(mock(LimitCheckResult.class)), List.of(TUK00));

    var funds = List.of(TUK75, TUK00);
    when(limitCheckService.runChecksForFunds(funds))
        .thenThrow(new LimitCheckPartialFailureException("1 fund(s) failed", partial));

    job.onNavCalculationCompleted(new NavCalculationCompleted(funds));

    verify(limitCheckNotifier).notify(partial);
    verify(pipelineTracker).stepFailed(any(), eq("1 fund(s) failed"));
  }

  @Test
  void partialFailureWithoutBreachesStillNamesTheFundsItCouldNotCheck() {
    var okResult = mock(LimitCheckResult.class);
    var partial = new LimitCheckRun(List.of(okResult), List.of(TUK00));

    var funds = List.of(TUK75, TUK00);
    when(limitCheckService.runChecksForFunds(funds))
        .thenThrow(new LimitCheckPartialFailureException("1 fund(s) failed", partial));

    job.onNavCalculationCompleted(new NavCalculationCompleted(funds));

    verify(limitCheckNotifier).notify(partial);
    verify(pipelineTracker).stepFailed(any(), eq("1 fund(s) failed"));
  }

  @Test
  void backfillFailureIsReportedRatherThanOnlyLogged() {
    var failure = new RuntimeException("DB down");
    when(limitCheckService.backfillChecks(25)).thenThrow(failure);

    job.backfillLimitChecks();

    verify(limitCheckNotifier).notifyBackfillFailed(failure);
    verify(limitCheckNotifier, never()).notify(any());
  }
}
