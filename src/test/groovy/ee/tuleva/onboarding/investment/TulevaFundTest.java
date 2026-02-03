package ee.tuleva.onboarding.investment;

import static ee.tuleva.onboarding.investment.TulevaFund.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class TulevaFundTest {

  @Test
  void getPillar2Funds_returnsPillar2FundsOnly() {
    List<TulevaFund> funds = getPillar2Funds();

    assertThat(funds).containsExactly(TUK75, TUK00);
    assertThat(funds).allMatch(fund -> fund.getPillar() == 2);
  }

  @Test
  void getPillar3Funds_returnsPillar3FundsOnly() {
    List<TulevaFund> funds = getPillar3Funds();

    assertThat(funds).containsExactly(TUV100, TKF100);
    assertThat(funds).allMatch(fund -> fund.getPillar() == 3);
  }

  @Test
  void enumValues_haveCorrectCodes() {
    assertThat(TUK75.getCode()).isEqualTo("TUK75");
    assertThat(TUK00.getCode()).isEqualTo("TUK00");
    assertThat(TUV100.getCode()).isEqualTo("TUV100");
    assertThat(TKF100.getCode()).isEqualTo("TKF100");
  }
}
