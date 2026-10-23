package ee.tuleva.onboarding.investment.kid;

import static ee.tuleva.onboarding.investment.kid.ScenarioPresentation.ANNEX_VI_P90_EXAMPLE_INVESTMENT_EUR;
import static java.math.RoundingMode.HALF_UP;

import java.math.BigDecimal;
import java.util.stream.IntStream;
import org.springframework.stereotype.Component;

@Component
class CostsOverTimeCalculator {

  private static final int RATE_SCALE = 8;
  private static final int EUR_SCALE = 2;

  KidCost firstYearAtZeroNetReturn(double ongoingCharges) {
    return costs(1, 1 / (1 - ongoingCharges), ongoingCharges);
  }

  KidCost holdingPeriod(int years, ScenarioWindow moderate, double ongoingCharges) {
    return costs(years, Math.pow(moderate.grossFactor(), 1.0 / years), ongoingCharges);
  }

  private KidCost costs(int years, double grossYearlyFactor, double ongoingCharges) {
    var example = ANNEX_VI_P90_EXAMPLE_INVESTMENT_EUR.doubleValue();
    var totalCosts =
        IntStream.rangeClosed(1, years)
            .mapToDouble(
                year ->
                    example
                        * Math.pow(grossYearlyFactor, year)
                        * Math.pow(1 - ongoingCharges, year - 1)
                        * ongoingCharges)
            .sum();
    return new KidCost(
        years,
        BigDecimal.valueOf(totalCosts).setScale(EUR_SCALE, HALF_UP),
        rate(grossYearlyFactor * ongoingCharges),
        rate(grossYearlyFactor - 1),
        rate(grossYearlyFactor * (1 - ongoingCharges) - 1));
  }

  private static BigDecimal rate(double value) {
    return BigDecimal.valueOf(value).setScale(RATE_SCALE, HALF_UP);
  }
}
