package ee.tuleva.onboarding.investment.transaction.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SebClientNameToFundResolverTest {

  private final SebClientNameToFundResolver resolver = new SebClientNameToFundResolver();

  @Test
  void resolve_tkf100() {
    assertThat(resolver.resolve("Tuleva Täiendav Kogumisfond")).contains(TulevaFund.TKF100);
  }

  @Test
  void resolve_tuk75() {
    assertThat(resolver.resolve("Tuleva Maailma Aktsiate Pensionifond")).contains(TulevaFund.TUK75);
  }

  @Test
  void resolve_tuk00() {
    assertThat(resolver.resolve("Tuleva Maailma Võlakirjade Pensionifond"))
        .contains(TulevaFund.TUK00);
  }

  @Test
  void resolve_tuv100() {
    assertThat(resolver.resolve("Tuleva III Samba Pensionifond")).contains(TulevaFund.TUV100);
  }

  @Test
  void resolve_trimsWhitespace() {
    assertThat(resolver.resolve("  Tuleva Täiendav Kogumisfond  ")).contains(TulevaFund.TKF100);
  }

  @Test
  void resolve_unknownName_returnsEmpty() {
    assertThat(resolver.resolve("Some Unknown Fund")).isEqualTo(Optional.empty());
  }

  @Test
  void resolve_nullName_returnsEmpty() {
    assertThat(resolver.resolve(null)).isEqualTo(Optional.empty());
  }

  @Test
  void resolve_blankName_returnsEmpty() {
    assertThat(resolver.resolve("   ")).isEqualTo(Optional.empty());
  }

  @Test
  void match_acceptsDiacriticStrippedClientName_returnsCorrectFund() {
    assertThat(resolver.resolve("Tuleva Maailma Volakirjade Pensionifond"))
        .contains(TulevaFund.TUK00);
  }

  @Test
  void match_acceptsCanonicalClientName_returnsCorrectFund() {
    assertThat(resolver.resolve("Tuleva Maailma Võlakirjade Pensionifond"))
        .contains(TulevaFund.TUK00);
  }

  @Test
  void match_acceptsTaiendavKogumisfond_returnsCorrectFund() {
    assertThat(resolver.resolve("Tuleva Taiendav Kogumisfond")).contains(TulevaFund.TKF100);
  }

  @Test
  void match_handlesTrailingWhitespace() {
    assertThat(resolver.resolve("Tuleva III Samba Pensionifond ")).contains(TulevaFund.TUV100);
  }
}
