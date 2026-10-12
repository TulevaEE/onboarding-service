package ee.tuleva.onboarding.investment.cashbuffer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class BusinessDayOutflowsTest {

  @Test
  void totalsEachRunOfConsecutiveBusinessDaysFromEveryStartingDay() {
    var outflows = outflows("100.00", "0.00", "300.00", "50.00");

    assertThat(outflows.forwardRollingTotals(2))
        .containsExactly(amount("100.00"), amount("300.00"), amount("350.00"));
  }

  @Test
  void aHorizonLongerThanTheWindowIsOneTotalOfTheWholeWindow() {
    var outflows = outflows("100.00", "200.00");

    assertThat(outflows.forwardRollingTotals(4)).containsExactly(amount("300.00"));
  }

  @Test
  void refusesAHorizonBelowOneBusinessDay() {
    assertThatThrownBy(() -> outflows("100.00").forwardRollingTotals(0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void refusesAWindowWithNoBusinessDays() {
    assertThatThrownBy(() -> outflows().forwardRollingTotals(4))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static BusinessDayOutflows outflows(String... amounts) {
    return new BusinessDayOutflows(Arrays.stream(amounts).map(BigDecimal::new).toList());
  }

  private static BigDecimal amount(String value) {
    return new BigDecimal(value);
  }
}
