package ee.tuleva.onboarding.investment.risk;

import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.investment.event.RunRiskIndicatorRequested;
import ee.tuleva.onboarding.investment.risk.RiskIndicatorService.RiskIndicatorRun;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class RiskIndicatorJobTest {

  private final RiskIndicatorService service = Mockito.mock(RiskIndicatorService.class);
  private final RiskIndicatorNotifier notifier = Mockito.mock(RiskIndicatorNotifier.class);
  private final RiskIndicatorJob job = new RiskIndicatorJob(service, notifier);

  @Test
  void theDailyRunEvaluatesTheDefaultLookbackAndNotifies() {
    var run = new RiskIndicatorRun(LocalDate.of(2026, 8, 6), List.of(), List.of());
    given(service.evaluateAllFunds(RiskIndicatorService.DEFAULT_LOOKBACK_MONTHS)).willReturn(run);

    job.evaluate();

    Mockito.verify(notifier).notify(run);
  }

  @Test
  void aBackfillRequestEvaluatesTheRequestedLookback() {
    var run = new RiskIndicatorRun(LocalDate.of(2026, 8, 6), List.of(), List.of());
    given(service.evaluateAllFunds(120)).willReturn(run);

    job.onRiskIndicatorRequested(new RunRiskIndicatorRequested(120));

    Mockito.verify(notifier).notify(run);
  }
}
