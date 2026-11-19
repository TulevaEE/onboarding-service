package ee.tuleva.onboarding.investment.fees.ocf;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PublishedPrecisionTest {

  @Test
  void aNewKidOfAnyFundShowsTheOcfWithTwoDecimals() {
    assertThat(PublishedPrecision.forANewKid()).isEqualTo(new PublishedPrecision(2));
  }

  @Test
  void aFractionIsShownAsAPercentRoundedHalfUpAtThePublishedPrecision() {
    assertThat(PublishedPrecision.forANewKid().percent(new BigDecimal("0.00455")))
        .isEqualTo(new BigDecimal("0.46"));
    assertThat(new PublishedPrecision(1).percent(new BigDecimal("0.00450")))
        .isEqualTo(new BigDecimal("0.5"));
  }

  @Test
  void aFractionIsPrintedAsAFractionToThePublishedPercentDecimals() {
    assertThat(PublishedPrecision.forANewKid().printedFraction(new BigDecimal("0.00459700")))
        .isEqualTo(new BigDecimal("0.0046"));
    assertThat(new PublishedPrecision(1).printedFraction(new BigDecimal("0.00459700")))
        .isEqualTo(new BigDecimal("0.005"));
  }

  @Test
  void figuresDifferOnlyWhenTheyRoundApartAtThePublishedPrecision() {
    var twoDecimals = PublishedPrecision.forANewKid();
    assertThat(twoDecimals.differ(new BigDecimal("0.0046"), new BigDecimal("0.00464"))).isFalse();
    assertThat(twoDecimals.differ(new BigDecimal("0.0046"), new BigDecimal("0.00465"))).isTrue();

    var oneDecimal = new PublishedPrecision(1);
    assertThat(oneDecimal.differ(new BigDecimal("0.0052"), new BigDecimal("0.0049"))).isFalse();
    assertThat(oneDecimal.differ(new BigDecimal("0.0052"), new BigDecimal("0.0044"))).isTrue();
  }
}
