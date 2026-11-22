package ee.tuleva.onboarding.investment.kid;

import static ee.tuleva.onboarding.investment.kid.ScenarioKind.FAVOURABLE;
import static ee.tuleva.onboarding.investment.kid.ScenarioKind.MODERATE;
import static ee.tuleva.onboarding.investment.kid.ScenarioKind.STRESS;
import static ee.tuleva.onboarding.investment.kid.ScenarioKind.UNFAVOURABLE;
import static ee.tuleva.onboarding.investment.kid.WindowSource.STRESSED_VOLATILITY;
import static java.math.RoundingMode.HALF_UP;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
class ScenarioPresentation {

  static final BigDecimal ANNEX_VI_P90_EXAMPLE_INVESTMENT_EUR = BigDecimal.valueOf(10_000);
  private static final int ANNEX_IV_P42_NEAREST_TEN_EUROS = -1;
  static final int STORED_UNROUNDED_SCALE = 8;
  private static final int PRINTED_RETURN_PERCENT_TO_TWO_DECIMALS_AS_A_FRACTION_SCALE = 4;

  List<KidScenario> present(
      int horizonYears,
      PerformanceScenarios performance,
      StressScenario stress,
      double ongoingCharges) {
    var unfavourable = Shown.of(performance.unfavourable(), ongoingCharges);
    var stressNetFactor = Math.exp(stress.logReturn()) * Math.pow(1 - ongoingCharges, horizonYears);
    var stressShown =
        stressNetFactor <= unfavourable.netFactor()
            ? new Shown(stress.start(), stress.end(), stressNetFactor)
            : unfavourable;
    return List.of(
        stressShown.as(STRESS, horizonYears),
        unfavourable.as(UNFAVOURABLE, horizonYears),
        Shown.of(performance.moderate(), ongoingCharges).as(MODERATE, horizonYears),
        Shown.of(performance.favourable(), ongoingCharges).as(FAVOURABLE, horizonYears));
  }

  private record Shown(
      LocalDate start,
      LocalDate end,
      WindowSource window,
      @Nullable Integer windowMonths,
      double netFactor) {

    Shown(LocalDate start, LocalDate end, double netFactor) {
      this(start, end, STRESSED_VOLATILITY, null, netFactor);
    }

    static Shown of(ScenarioWindow window, double ongoingCharges) {
      return new Shown(
          window.start(),
          window.end(),
          window.source(),
          window.months(),
          window.netFactor(ongoingCharges));
    }

    KidScenario as(ScenarioKind kind, int horizonYears) {
      var amount = BigDecimal.valueOf(netFactor).multiply(ANNEX_VI_P90_EXAMPLE_INVESTMENT_EUR);
      var averageReturnPerYear = BigDecimal.valueOf(Math.pow(netFactor, 1.0 / horizonYears) - 1);
      return new KidScenario(
          horizonYears,
          kind,
          amount.setScale(ANNEX_IV_P42_NEAREST_TEN_EUROS, HALF_UP).setScale(0),
          averageReturnPerYear.setScale(STORED_UNROUNDED_SCALE, HALF_UP),
          start,
          end,
          window,
          windowMonths,
          amount.setScale(STORED_UNROUNDED_SCALE, HALF_UP),
          averageReturnPerYear.setScale(
              PRINTED_RETURN_PERCENT_TO_TWO_DECIMALS_AS_A_FRACTION_SCALE, HALF_UP));
    }
  }
}
