package ee.tuleva.onboarding.deadline;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class BusinessDaysTest {

  private final BusinessDays businessDays = new BusinessDays(new PublicHolidays());

  @Test
  void firstBusinessDayIsTheFirstOfMonthWhenItIsAWorkingDay() {
    assertThat(businessDays.isNthBusinessDayOfMonth(LocalDate.of(2026, 6, 1), 1)).isTrue();
  }

  @Test
  void firstBusinessDaySkipsAWeekendStartOfMonth() {
    assertThat(businessDays.isNthBusinessDayOfMonth(LocalDate.of(2026, 8, 1), 1)).isFalse();
    assertThat(businessDays.isNthBusinessDayOfMonth(LocalDate.of(2026, 8, 2), 1)).isFalse();
    assertThat(businessDays.isNthBusinessDayOfMonth(LocalDate.of(2026, 8, 3), 1)).isTrue();
  }

  @Test
  void firstBusinessDaySkipsAPublicHolidayStartOfMonth() {
    assertThat(businessDays.isNthBusinessDayOfMonth(LocalDate.of(2026, 1, 1), 1)).isFalse();
    assertThat(businessDays.isNthBusinessDayOfMonth(LocalDate.of(2026, 1, 2), 1)).isTrue();
  }

  @Test
  void nthBusinessDayCountsForwardFromAWeekendStartOfMonth() {
    assertThat(businessDays.isNthBusinessDayOfMonth(LocalDate.of(2026, 8, 4), 2)).isTrue();
    assertThat(businessDays.isNthBusinessDayOfMonth(LocalDate.of(2026, 8, 5), 3)).isTrue();
    assertThat(businessDays.isNthBusinessDayOfMonth(LocalDate.of(2026, 8, 6), 4)).isTrue();
  }

  @Test
  void nthBusinessDayCountsForwardFromAPublicHolidayStartOfMonth() {
    assertThat(businessDays.isNthBusinessDayOfMonth(LocalDate.of(2026, 1, 5), 2)).isTrue();
    assertThat(businessDays.isNthBusinessDayOfMonth(LocalDate.of(2026, 1, 6), 3)).isTrue();
  }

  @Test
  void nthBusinessDaySkipsHolidaysInTheMiddleOfTheCount() {
    assertThat(businessDays.isNthBusinessDayOfMonth(LocalDate.of(2026, 4, 2), 2)).isTrue();
    assertThat(businessDays.isNthBusinessDayOfMonth(LocalDate.of(2026, 4, 3), 3)).isFalse();
    assertThat(businessDays.isNthBusinessDayOfMonth(LocalDate.of(2026, 4, 6), 3)).isTrue();
    assertThat(businessDays.isNthBusinessDayOfMonth(LocalDate.of(2026, 4, 7), 4)).isTrue();
  }

  @Test
  void fourthBusinessDayFromASundayStartOfMonth() {
    assertThat(businessDays.isNthBusinessDayOfMonth(LocalDate.of(2026, 2, 5), 4)).isTrue();
    assertThat(businessDays.isNthBusinessDayOfMonth(LocalDate.of(2026, 2, 4), 4)).isFalse();
  }

  @Test
  void fourthBusinessDayFromASpringDayHolidayStartOfMonth() {
    assertThat(businessDays.isNthBusinessDayOfMonth(LocalDate.of(2026, 5, 7), 4)).isTrue();
    assertThat(businessDays.isNthBusinessDayOfMonth(LocalDate.of(2026, 5, 6), 4)).isFalse();
  }

  @Test
  void aDateThatIsNotTheNthBusinessDayIsRejected() {
    assertThat(businessDays.isNthBusinessDayOfMonth(LocalDate.of(2026, 8, 5), 4)).isFalse();
    assertThat(businessDays.isNthBusinessDayOfMonth(LocalDate.of(2026, 8, 31), 3)).isFalse();
  }
}
