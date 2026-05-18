package ee.tuleva.onboarding.investment.transaction.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PriceValidatorTest {

  private static final BigDecimal TOLERANCE = new BigDecimal("0.01");

  private final PriceValidator validator = new PriceValidator();

  @Test
  void zeroDelta_isWithinTolerance() {
    assertThat(
            validator.isWithinTolerance(
                new BigDecimal("4.7255"), new BigDecimal("4.7255"), TOLERANCE))
        .isTrue();
  }

  @Test
  void justBelowOnePercent_isWithinTolerance() {
    // |100 - 100.99| / 100.99 ≈ 0.98% < 1.0%
    assertThat(
            validator.isWithinTolerance(new BigDecimal("100"), new BigDecimal("100.99"), TOLERANCE))
        .isTrue();
  }

  @Test
  void exactlyOnePercent_isWithinTolerance() {
    // |99 - 100| / 100 = 1.00%
    assertThat(validator.isWithinTolerance(new BigDecimal("99"), new BigDecimal("100"), TOLERANCE))
        .isTrue();
  }

  @Test
  void justAboveOnePercent_isOutsideTolerance() {
    // |98.99 - 100| / 100 = 1.01%
    assertThat(
            validator.isWithinTolerance(new BigDecimal("98.99"), new BigDecimal("100"), TOLERANCE))
        .isFalse();
  }

  @Test
  void priceAboveNav_usesAbsoluteDelta() {
    // |101.5 - 100| / 100 = 1.5% > 1.0%
    assertThat(
            validator.isWithinTolerance(new BigDecimal("101.5"), new BigDecimal("100"), TOLERANCE))
        .isFalse();
  }

  @Test
  void computeDeltaPercent_returnsAbsoluteRatioTimes100() {
    // |4.7255 - 4.7800| / 4.7800 ≈ 0.011401673
    BigDecimal delta =
        validator.computeDeltaPercent(new BigDecimal("4.7255"), new BigDecimal("4.7800"));
    assertThat(delta).isBetween(new BigDecimal("1.13"), new BigDecimal("1.15"));
  }
}
