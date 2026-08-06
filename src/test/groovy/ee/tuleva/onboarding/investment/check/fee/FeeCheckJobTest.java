package ee.tuleva.onboarding.investment.check.fee;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.event.PipelineStep.FEE_CHECK;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.event.PipelineTracker;
import ee.tuleva.onboarding.investment.event.RunFeeCheckRequested;
import ee.tuleva.onboarding.savings.fund.nav.NavCalculationCompleted;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FeeCheckJobTest {

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");
  private static final LocalDate TODAY = LocalDate.of(2026, 6, 4);

  @Mock private FeeCheckService feeCheckService;
  @Mock private PipelineTracker pipelineTracker;

  private final Clock clock = Clock.fixed(instantAt(TODAY), TALLINN);

  @Test
  void navCalculationCompletedRunsTheDailyChecksForThatRunsFunds() {
    job().onNavCalculationCompleted(new NavCalculationCompleted(List.of(TUK75)));

    verify(feeCheckService).runDailyChecks(List.of(TUK75), TODAY);
    verify(pipelineTracker).stepCompleted(FEE_CHECK);
  }

  @Test
  void anOnDemandRequestChecksEveryFund() {
    job().onFeeCheckRequested(new RunFeeCheckRequested());

    verify(feeCheckService).runDailyChecks(List.of(TulevaFund.values()), TODAY);
  }

  @Test
  void aFailingCheckIsReportedToThePipelineAndNeverPropagates() {
    willThrow(new IllegalStateException("boom"))
        .given(feeCheckService)
        .runDailyChecks(any(), any());

    job().onNavCalculationCompleted(new NavCalculationCompleted(List.of(TUK75)));

    verify(pipelineTracker).stepFailed(eq(FEE_CHECK), any());
  }

  private FeeCheckJob job() {
    return new FeeCheckJob(feeCheckService, pipelineTracker, clock);
  }

  private static Instant instantAt(LocalDate date) {
    return date.atStartOfDay(TALLINN).toInstant();
  }
}
