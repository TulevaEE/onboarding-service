package ee.tuleva.onboarding.investment.position;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TulevaFundTest {

  @Test
  void normalize_returnsTUK75_forAktsiatePortfolio() {
    assertThat(TulevaFund.normalize("Tuleva Maailma Aktsiate Pensionifond")).isEqualTo("TUK75");
  }

  @Test
  void normalize_returnsTUK00_forVolakirjadePortfolio() {
    assertThat(TulevaFund.normalize("Tuleva Maailma Volakirjade Pensionifond")).isEqualTo("TUK00");
  }

  @Test
  void normalize_returnsTUK00_forVolakirjadePortfolioWithEstonianO() {
    assertThat(TulevaFund.normalize("Tuleva Maailma Võlakirjade Pensionifond")).isEqualTo("TUK00");
  }

  @Test
  void normalize_returnsTUV100_forVabatahtlikPortfolio() {
    assertThat(TulevaFund.normalize("Tuleva Vabatahtlik Pensionifond")).isEqualTo("TUV100");
  }

  @Test
  void normalize_returnsTUV100_forVabatahtlikPortfolioWithTypo() {
    assertThat(TulevaFund.normalize("Tuleva Vabatahtlik Pensionifon")).isEqualTo("TUV100");
  }

  @Test
  void normalize_isCaseInsensitive() {
    assertThat(TulevaFund.normalize("TULEVA MAAILMA AKTSIATE PENSIONIFOND")).isEqualTo("TUK75");
  }

  @Test
  void normalize_trimsWhitespace() {
    assertThat(TulevaFund.normalize("  Tuleva Maailma Aktsiate Pensionifond  ")).isEqualTo("TUK75");
  }

  @Test
  void normalize_throwsException_forUnknownPortfolio() {
    assertThatThrownBy(() -> TulevaFund.normalize("Unknown Portfolio"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unknown portfolio");
  }
}
