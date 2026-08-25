package ee.tuleva.onboarding.fund;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TulevaFundTest {

  @Test
  void savingsFundIsTheOnlyFundWithoutAPillar() {
    assertThat(TulevaFund.getSavingsFunds()).containsExactly(TulevaFund.TKF100);
    assertThat(TulevaFund.getPillar2Funds()).containsExactly(TulevaFund.TUK75, TulevaFund.TUK00);
    assertThat(TulevaFund.getPillar3Funds()).containsExactly(TulevaFund.TUV100);
  }
}
