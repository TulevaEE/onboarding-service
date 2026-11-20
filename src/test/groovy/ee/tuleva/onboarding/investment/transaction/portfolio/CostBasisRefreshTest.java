package ee.tuleva.onboarding.investment.transaction.portfolio;

import static ee.tuleva.onboarding.investment.transaction.portfolio.CostBasisRefresh.Outcome.FAILED;
import static ee.tuleva.onboarding.investment.transaction.portfolio.CostBasisRefresh.Outcome.LOCK_NOT_TAKEN;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CostBasisRefreshTest {

  @Test
  void failedRefreshWithoutAFailure_isRejected() {
    assertThatThrownBy(() -> new CostBasisRefresh(FAILED, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void refreshThatDidNotFailButCarriesAFailure_isRejected() {
    assertThatThrownBy(() -> new CostBasisRefresh(LOCK_NOT_TAKEN, "Cost-basis oversell"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
