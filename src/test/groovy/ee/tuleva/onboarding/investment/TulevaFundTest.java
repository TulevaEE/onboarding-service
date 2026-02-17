package ee.tuleva.onboarding.investment;

import static ee.tuleva.onboarding.investment.TulevaFund.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class TulevaFundTest {

  @Test
  void getPillar2Funds_returnsPillar2FundsOnly() {
    List<TulevaFund> funds = getPillar2Funds();

    assertThat(funds).containsExactly(TUK75, TUK00);
    assertThat(funds).allMatch(fund -> fund.getPillar() != null && fund.getPillar() == 2);
  }

  @Test
  void getPillar3Funds_returnsPillar3FundsOnly() {
    List<TulevaFund> funds = getPillar3Funds();

    assertThat(funds).containsExactly(TUV100);
    assertThat(funds).allMatch(fund -> fund.getPillar() != null && fund.getPillar() == 3);
  }

  @Test
  void enumValues_haveCorrectCodes() {
    assertThat(TUK75.getCode()).isEqualTo("TUK75");
    assertThat(TUK00.getCode()).isEqualTo("TUK00");
    assertThat(TUV100.getCode()).isEqualTo("TUV100");
    assertThat(TKF100.getCode()).isEqualTo("TKF100");
  }

  @Test
  void tkf100_hasNullPillar() {
    assertThat(TKF100.getPillar()).isNull();
    assertThat(TKF100.getIsin()).isEqualTo("EE0000003283");
    assertThat(TKF100.getAumKey()).isEqualTo("AUM_EE0000003283");
  }

  @Test
  void tkf100_notIncludedInPillar2Or3Funds() {
    assertThat(getPillar2Funds()).doesNotContain(TKF100);
    assertThat(getPillar3Funds()).doesNotContain(TKF100);
  }

  @Test
  void getSavingsFunds_returnsTkf100Only() {
    List<TulevaFund> funds = getSavingsFunds();

    assertThat(funds).containsExactly(TKF100);
    assertThat(funds).allMatch(fund -> fund.getPillar() == null);
  }

  @Test
  void fromCode_returnsCorrectFund() {
    assertThat(fromCode("TUK75")).isEqualTo(TUK75);
    assertThat(fromCode("TKF100")).isEqualTo(TKF100);
  }

  @Test
  void fromCode_throwsForUnknownCode() {
    assertThatThrownBy(() -> fromCode("UNKNOWN"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Unknown fund code: UNKNOWN");
  }
}
