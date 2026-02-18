package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.FUND;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class SettlementDateCalculatorTest {

  private final SettlementDateCalculator calculator = new SettlementDateCalculator();

  @Test
  void etf_settlesInTwoDays() {
    LocalDate tradeDate = LocalDate.of(2026, 1, 12);
    LocalDate expected = LocalDate.of(2026, 1, 14);

    assertThat(calculator.calculateSettlementDate(tradeDate, ETF)).isEqualTo(expected);
  }

  @Test
  void fund_settlesInFiveDays() {
    LocalDate tradeDate = LocalDate.of(2026, 1, 12);
    LocalDate expected = LocalDate.of(2026, 1, 19);

    assertThat(calculator.calculateSettlementDate(tradeDate, FUND)).isEqualTo(expected);
  }

  @Test
  void etf_skipsWeekends() {
    LocalDate friday = LocalDate.of(2026, 1, 9);
    LocalDate expectedTuesday = LocalDate.of(2026, 1, 13);

    assertThat(calculator.calculateSettlementDate(friday, ETF)).isEqualTo(expectedTuesday);
  }

  @Test
  void fund_skipsWeekends() {
    LocalDate wednesday = LocalDate.of(2026, 1, 7);
    LocalDate expectedWednesday = LocalDate.of(2026, 1, 14);

    assertThat(calculator.calculateSettlementDate(wednesday, FUND)).isEqualTo(expectedWednesday);
  }

  @Test
  void etf_fromThursday_landOnMonday() {
    LocalDate thursday = LocalDate.of(2026, 1, 8);
    LocalDate expectedMonday = LocalDate.of(2026, 1, 12);

    assertThat(calculator.calculateSettlementDate(thursday, ETF)).isEqualTo(expectedMonday);
  }
}
