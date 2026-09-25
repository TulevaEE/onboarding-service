package ee.tuleva.onboarding.investment.check.tracking;

import static java.math.RoundingMode.HALF_UP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

class YearFractionTest {

  private static final Offset<BigDecimal> EXACT_ENOUGH = within(new BigDecimal("1e-18"));

  @Test
  void aDayInAnOrdinaryYearIsOneThreeHundredSixtyFifth() {
    var fraction =
        YearFraction.eachDayWeighedByItsOwnYear(
            LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30));

    assertThat(fraction)
        .isCloseTo(new BigDecimal("30").divide(new BigDecimal("365"), 20, HALF_UP), EXACT_ENOUGH);
  }

  @Test
  void aDayInALeapYearIsOneThreeHundredSixtySixth() {
    var fraction =
        YearFraction.eachDayWeighedByItsOwnYear(
            LocalDate.of(2028, 2, 1), LocalDate.of(2028, 2, 29));

    assertThat(fraction)
        .isCloseTo(new BigDecimal("29").divide(new BigDecimal("366"), 20, HALF_UP), EXACT_ENOUGH);
  }

  @Test
  void aWholeLeapYearIsExactlyOneYear() {
    var fraction =
        YearFraction.eachDayWeighedByItsOwnYear(
            LocalDate.of(2028, 1, 1), LocalDate.of(2028, 12, 31));

    assertThat(fraction).isEqualByComparingTo(BigDecimal.ONE);
  }

  @Test
  void daysEitherSideOfNewYearAreWeighedByTheirOwnYear() {
    var fraction =
        YearFraction.eachDayWeighedByItsOwnYear(
            LocalDate.of(2027, 12, 31), LocalDate.of(2028, 1, 2));

    var oneDayOf2027 = BigDecimal.ONE.divide(new BigDecimal("365"), 20, HALF_UP);
    var twoDaysOf2028 = new BigDecimal("2").divide(new BigDecimal("366"), 20, HALF_UP);
    assertThat(fraction).isCloseTo(oneDayOf2027.add(twoDaysOf2028), EXACT_ENOUGH);
  }

  @Test
  void anEmptyRunOfDaysIsNoShareOfAYear() {
    var fraction =
        YearFraction.eachDayWeighedByItsOwnYear(LocalDate.of(2026, 4, 2), LocalDate.of(2026, 4, 1));

    assertThat(fraction).isEqualByComparingTo(BigDecimal.ZERO);
  }
}
