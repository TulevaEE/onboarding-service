package ee.tuleva.onboarding.investment.kid;

import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

class KidCalculationTest {

  @Test
  void aCalculationWithNeitherFiguresNorARefusalReasonCannotExist() {
    assertThatThrownBy(() -> KidCalculation.refused(TKF100, YearMonth.of(2026, 9), List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
