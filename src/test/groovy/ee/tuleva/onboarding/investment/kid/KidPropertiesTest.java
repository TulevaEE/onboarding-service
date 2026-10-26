package ee.tuleva.onboarding.investment.kid;

import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ee.tuleva.onboarding.investment.kid.KidProperties.FundSettings;
import org.junit.jupiter.api.Test;

class KidPropertiesTest {

  @Test
  void refusesEveryFundWhenNoKidFundsAreConfigured() {
    var properties = new KidProperties(null);

    assertThatThrownBy(() -> properties.settingsFor(TKF100))
        .isInstanceOf(KidCalculationRefusedException.class);
  }

  @Test
  void refusesAHoldingPeriodNoLongerThanTheOneYearPeriodItIsShownBeside() {
    assertThatThrownBy(() -> new FundSettings("MSCI_ACWI", 1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void acceptsAFiveYearHoldingPeriod() {
    assertThatCode(() -> new FundSettings("MSCI_ACWI", 5)).doesNotThrowAnyException();
  }

  @Test
  void refusesAHoldingPeriodLongerThanFiveYearsWhoseLongerScenarioPeriodIsNotBuilt() {
    assertThatThrownBy(() -> new FundSettings("MSCI_ACWI", 6))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
