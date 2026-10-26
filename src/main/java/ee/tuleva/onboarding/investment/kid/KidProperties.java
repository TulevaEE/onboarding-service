package ee.tuleva.onboarding.investment.kid;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "investment.kid")
record KidProperties(Map<TulevaFund, FundSettings> funds) {

  KidProperties {
    funds = funds == null ? Map.of() : Map.copyOf(funds);
  }

  FundSettings settingsFor(TulevaFund fund) {
    var settings = funds.get(fund);
    if (settings == null) {
      throw new KidCalculationRefusedException("No KID series configured: fund=" + fund);
    }
    return settings;
  }

  record FundSettings(String seriesKey, int recommendedHoldingPeriodYears) {

    private static final int ANNEX_IV_P32_INTERMEDIATE_PERIOD_YEARS = 1;
    private static final int ANNEX_IV_P6_TEN_YEAR_PERIOD_HOLDS_HOLDING_PERIODS_UP_TO_YEARS = 5;

    FundSettings {
      if (recommendedHoldingPeriodYears <= ANNEX_IV_P32_INTERMEDIATE_PERIOD_YEARS) {
        throw new IllegalArgumentException(
            "Recommended holding period must be longer than the one-year period:"
                + " recommendedHoldingPeriodYears="
                + recommendedHoldingPeriodYears);
      }
      if (recommendedHoldingPeriodYears
          > ANNEX_IV_P6_TEN_YEAR_PERIOD_HOLDS_HOLDING_PERIODS_UP_TO_YEARS) {
        throw new IllegalArgumentException(
            "Recommended holding period longer than five years needs a period of the holding"
                + " period plus five years, which is not built: recommendedHoldingPeriodYears="
                + recommendedHoldingPeriodYears);
      }
    }
  }
}
