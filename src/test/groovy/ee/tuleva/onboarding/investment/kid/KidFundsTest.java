package ee.tuleva.onboarding.investment.kid;

import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ee.tuleva.onboarding.investment.kid.KidFunds.FundSettings;
import java.util.Map;
import org.junit.jupiter.api.Test;

class KidFundsTest {

  @Test
  void refusesAFundWithoutKidSettings() {
    var funds = new KidFunds(Map.of());

    assertThatThrownBy(() -> funds.settingsFor(TKF100))
        .isInstanceOf(KidCalculationRefusedException.class);
  }

  @Test
  void refusesAHoldingPeriodNoLongerThanTheOneYearPeriodItIsShownBeside() {
    assertThatThrownBy(() -> new FundSettings(1)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void acceptsAFiveYearHoldingPeriod() {
    assertThatCode(() -> new FundSettings(5)).doesNotThrowAnyException();
  }

  @Test
  void refusesAHoldingPeriodLongerThanFiveYearsWhoseLongerScenarioPeriodIsNotBuilt() {
    assertThatThrownBy(() -> new FundSettings(6)).isInstanceOf(IllegalArgumentException.class);
  }
}
