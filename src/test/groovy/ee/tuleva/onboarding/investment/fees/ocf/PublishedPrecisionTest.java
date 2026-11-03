package ee.tuleva.onboarding.investment.fees.ocf;

import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PublishedPrecisionTest {

  @Test
  void aPensionFundKidShowsTheOcfWithTwoDecimalsAndTheSavingsFundKidWithOne() {
    assertThat(PublishedPrecision.of(TUK75)).isEqualTo(new PublishedPrecision(2));
    assertThat(PublishedPrecision.of(TUK00)).isEqualTo(new PublishedPrecision(2));
    assertThat(PublishedPrecision.of(TUV100)).isEqualTo(new PublishedPrecision(2));
    assertThat(PublishedPrecision.of(TKF100)).isEqualTo(new PublishedPrecision(1));
  }

  @Test
  void aFractionIsShownAsAPercentRoundedHalfUpAtThePublishedPrecision() {
    assertThat(PublishedPrecision.of(TUK75).percent(new BigDecimal("0.00455")))
        .isEqualTo(new BigDecimal("0.46"));
    assertThat(PublishedPrecision.of(TKF100).percent(new BigDecimal("0.00450")))
        .isEqualTo(new BigDecimal("0.5"));
  }

  @Test
  void aFractionIsPrintedAsAFractionToThePublishedPercentDecimals() {
    assertThat(PublishedPrecision.of(TUK75).printedFraction(new BigDecimal("0.00459700")))
        .isEqualTo(new BigDecimal("0.0046"));
    assertThat(PublishedPrecision.of(TKF100).printedFraction(new BigDecimal("0.00459700")))
        .isEqualTo(new BigDecimal("0.005"));
  }

  @Test
  void figuresDifferOnlyWhenTheyRoundApartAtThePublishedPrecision() {
    var pensionFund = PublishedPrecision.of(TUK75);
    assertThat(pensionFund.differ(new BigDecimal("0.0046"), new BigDecimal("0.00464"))).isFalse();
    assertThat(pensionFund.differ(new BigDecimal("0.0046"), new BigDecimal("0.00465"))).isTrue();

    var savingsFund = PublishedPrecision.of(TKF100);
    assertThat(savingsFund.differ(new BigDecimal("0.0052"), new BigDecimal("0.0049"))).isFalse();
    assertThat(savingsFund.differ(new BigDecimal("0.0052"), new BigDecimal("0.0044"))).isTrue();
  }
}
