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
  private static final int RETURN_SCALE = 8;

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
      return new KidScenario(
          horizonYears,
          kind,
          BigDecimal.valueOf(netFactor)
              .multiply(ANNEX_VI_P90_EXAMPLE_INVESTMENT_EUR)
              .setScale(ANNEX_IV_P42_NEAREST_TEN_EUROS, HALF_UP)
              .setScale(0),
          BigDecimal.valueOf(Math.pow(netFactor, 1.0 / horizonYears) - 1)
              .setScale(RETURN_SCALE, HALF_UP),
          start,
          end,
          window,
          windowMonths);
    }
  }
}
