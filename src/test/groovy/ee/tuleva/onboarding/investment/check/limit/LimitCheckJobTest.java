package ee.tuleva.onboarding.investment.check.limit;

import static ee.tuleva.onboarding.investment.check.limit.LimitCheckJob.GAP_LOOKBACK_DAYS;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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

  // The limit check already runs on NavCalculationCompleted, so a normal day has no gaps at all.
  // This is a backstop, and a backstop that posts every day trains people to ignore it.
  @Test
  void aDayWithNoGapsSaysNothingAndDoesNotSyncPositions() {
    when(limitCheckService.gapDates(GAP_LOOKBACK_DAYS)).thenReturn(Map.of());

    job.fillLimitCheckGaps();

    verify(feeAccrualPositionSyncJob, never()).sync(anyInt());
    verify(limitCheckService, never()).fillGaps(any());
    verifyNoInteractions(limitCheckNotifier);
  }

  // The retired yearly backfill synced fee accrual positions before checking, because the checks
  // read them. Losing that ordering would make the filled days quietly wrong rather than missing.
  @Test
  void positionsAreSyncedBeforeAnyGapIsChecked() {
    var gaps = Map.of(TUK75, List.of(LocalDate.of(2026, 4, 9)));
    var run = LimitCheckRun.of(List.of(mock(LimitCheckResult.class)));
    when(limitCheckService.gapDates(GAP_LOOKBACK_DAYS)).thenReturn(gaps);
    when(limitCheckService.fillGaps(gaps)).thenReturn(run);

    job.fillLimitCheckGaps();

    var ordered = inOrder(feeAccrualPositionSyncJob, limitCheckService);
    ordered.verify(feeAccrualPositionSyncJob).sync(GAP_LOOKBACK_DAYS);
    ordered.verify(limitCheckService).fillGaps(gaps);
    verify(limitCheckNotifier).notify(run);
  }

  @Test
  void aFailedGapFillIsReportedRatherThanOnlyLogged() {
    when(limitCheckService.gapDates(GAP_LOOKBACK_DAYS)).thenThrow(new RuntimeException("DB down"));

    job.fillLimitCheckGaps();

    verify(limitCheckNotifier).notifyBackfillFailed(any(Exception.class));
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
