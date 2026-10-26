package ee.tuleva.onboarding.investment.kid.performance;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class EquityCapTest {

  private static final EquityCap CAP =
      new EquityCap("BOND", new BigDecimal("0.75"), LocalDate.of(2019, 9, 2));

  @ParameterizedTest
  @CsvSource({
    "2019, 2017-03-28, true",
    "2020, 2017-03-28, false",
    "2019, 2019-09-02, false",
    "2019, 2019-09-03, false"
  })
  void isHeldInAYearShownOnlyWhenThatYearAndTheFundsFirstDayCameBeforeTheLift(
      int firstYearShown, LocalDate fundStart, boolean held) {
    assertThat(CAP.heldInAYearFrom(firstYearShown, fundStart)).isEqualTo(held);
  }
}
