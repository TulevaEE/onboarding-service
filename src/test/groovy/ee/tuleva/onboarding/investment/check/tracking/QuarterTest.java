package ee.tuleva.onboarding.investment.check.tracking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class QuarterTest {

  @Test
  void theSecondQuarterRunsFromAprilThroughJune() {
    var quarter = new Quarter(2026, 2);

    assertThat(quarter.start()).isEqualTo(LocalDate.of(2026, 4, 1));
    assertThat(quarter.end()).isEqualTo(LocalDate.of(2026, 6, 30));
  }

  @Test
  void theFourthQuarterEndsOnTheLastDayOfTheYear() {
    assertThat(new Quarter(2026, 4).end()).isEqualTo(LocalDate.of(2026, 12, 31));
  }

  @Test
  void theFirstQuarterRunsFromJanuaryThroughMarch() {
    var quarter = new Quarter(2026, 1);

    assertThat(quarter.start()).isEqualTo(LocalDate.of(2026, 1, 1));
    assertThat(quarter.end()).isEqualTo(LocalDate.of(2026, 3, 31));
  }

  @Test
  void aQuarterBelowOneIsRefused() {
    assertThatThrownBy(() -> new Quarter(2026, 0)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aQuarterAboveFourIsRefused() {
    assertThatThrownBy(() -> new Quarter(2026, 5)).isInstanceOf(IllegalArgumentException.class);
  }
}
