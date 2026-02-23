package ee.tuleva.onboarding.investment.fees;

import static ee.tuleva.onboarding.investment.fees.FeeAccrualBuilder.daysInYear;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class FeeAccrualBuilderTest {

  @Test
  void daysInYear_returnsCorrectDaysForLeapAndNonLeapYears() {
    assertThat(daysInYear(LocalDate.of(2024, 6, 15))).isEqualTo(366);
    assertThat(daysInYear(LocalDate.of(2025, 6, 15))).isEqualTo(365);
    assertThat(daysInYear(LocalDate.of(2026, 1, 1))).isEqualTo(365);
    assertThat(daysInYear(LocalDate.of(2027, 12, 31))).isEqualTo(365);
  }
}
