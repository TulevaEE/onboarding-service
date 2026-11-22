package ee.tuleva.onboarding.investment.kid;

import java.math.BigDecimal;
import java.time.YearMonth;

record PublishedScenario(
    YearMonth month,
    int horizonYears,
    ScenarioKind scenario,
    BigDecimal amountEur,
    BigDecimal averageReturnPerYear) {

  static PublishedScenario of(YearMonth month, PrintedScenario printed) {
    return new PublishedScenario(
        month,
        printed.horizonYears(),
        printed.scenario(),
        printed.amountEur(),
        printed.averageReturnPerYear());
  }

  PrintedScenario printed() {
    return new PrintedScenario(horizonYears, scenario, amountEur, averageReturnPerYear);
  }
}
