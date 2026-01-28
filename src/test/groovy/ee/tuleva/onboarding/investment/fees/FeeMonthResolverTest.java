package ee.tuleva.onboarding.investment.fees;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class FeeMonthResolverTest {

  private final FeeMonthResolver resolver = new FeeMonthResolver();

  @Test
  void resolveFeeMonth_returnsFirstDayOfMonth() {
    LocalDate midMonth = LocalDate.of(2025, 1, 15);

    LocalDate result = resolver.resolveFeeMonth(midMonth);

    assertThat(result).isEqualTo(LocalDate.of(2025, 1, 1));
  }

  @Test
  void resolveFeeMonth_returnsFirstDayOfMonth_whenLastDayOfMonth() {
    LocalDate lastDay = LocalDate.of(2025, 1, 31);

    LocalDate result = resolver.resolveFeeMonth(lastDay);

    assertThat(result).isEqualTo(LocalDate.of(2025, 1, 1));
  }

  @Test
  void resolveFeeMonth_returnsFirstDayOfMonth_whenWeekend() {
    LocalDate sunday = LocalDate.of(2025, 8, 31);

    LocalDate result = resolver.resolveFeeMonth(sunday);

    assertThat(result).isEqualTo(LocalDate.of(2025, 8, 1));
  }

  @Test
  void resolveFeeMonth_returnsFirstDayOfMonth_whenFirstDayOfMonth() {
    LocalDate firstDay = LocalDate.of(2025, 2, 1);

    LocalDate result = resolver.resolveFeeMonth(firstDay);

    assertThat(result).isEqualTo(LocalDate.of(2025, 2, 1));
  }
}
