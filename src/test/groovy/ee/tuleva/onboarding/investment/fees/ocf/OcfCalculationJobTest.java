package ee.tuleva.onboarding.investment.fees.ocf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OcfCalculationJobTest {

  @Mock private OcfCalculationService service;
  @Mock private PublicHolidays publicHolidays;
  @Mock private Clock clock;

  @InjectMocks private OcfCalculationJob job;

  private static final ZoneId ZONE = ZoneId.of("Europe/Tallinn");

  @Test
  void computeMonthlyOnFourthBusinessDay() {
    var fourthBd = LocalDate.of(2026, 6, 4);
    setupClock(fourthBd);
    setupBusinessDaySequence(fourthBd, 4);

    job.computeMonthlyIfReady();

    verify(service).calculateForAllFunds(YearMonth.of(2026, 5));
  }

  @Test
  void skipWhenNotFourthBusinessDay() {
    var thirdBd = LocalDate.of(2026, 6, 3);
    setupClock(thirdBd);
    setupBusinessDaySequence(thirdBd, 3);

    job.computeMonthlyIfReady();

    verify(service, never()).calculateForAllFunds(any());
  }

  @Test
  void onOcfCalculationRequestedTriggersComputation() {
    var today = LocalDate.of(2026, 6, 15);
    setupClock(today);

    job.onOcfCalculationRequested();

    verify(service).calculateForAllFunds(YearMonth.of(2026, 5));
  }

  @Test
  void isNthBusinessDayHandlesWeekend() {
    var monday = LocalDate.of(2026, 6, 1);
    given(publicHolidays.isWorkingDay(monday)).willReturn(true);
    given(publicHolidays.nextWorkingDay(monday)).willReturn(LocalDate.of(2026, 6, 2));
    given(publicHolidays.nextWorkingDay(LocalDate.of(2026, 6, 2)))
        .willReturn(LocalDate.of(2026, 6, 3));
    given(publicHolidays.nextWorkingDay(LocalDate.of(2026, 6, 3)))
        .willReturn(LocalDate.of(2026, 6, 4));

    assertThat(job.isNthBusinessDayOfMonth(LocalDate.of(2026, 6, 4), 4)).isTrue();
    assertThat(job.isNthBusinessDayOfMonth(LocalDate.of(2026, 6, 3), 4)).isFalse();
  }

  @Test
  void isNthBusinessDayWhenFirstDayIsWeekend() {
    var saturday = LocalDate.of(2026, 8, 1);
    given(publicHolidays.isWorkingDay(saturday)).willReturn(false);
    var monday = LocalDate.of(2026, 8, 3);
    given(publicHolidays.nextWorkingDay(saturday)).willReturn(monday);
    given(publicHolidays.nextWorkingDay(monday)).willReturn(LocalDate.of(2026, 8, 4));
    given(publicHolidays.nextWorkingDay(LocalDate.of(2026, 8, 4)))
        .willReturn(LocalDate.of(2026, 8, 5));
    given(publicHolidays.nextWorkingDay(LocalDate.of(2026, 8, 5)))
        .willReturn(LocalDate.of(2026, 8, 6));

    assertThat(job.isNthBusinessDayOfMonth(LocalDate.of(2026, 8, 6), 4)).isTrue();
  }

  private void setupClock(LocalDate date) {
    var fixedClock = Clock.fixed(date.atStartOfDay(ZONE).toInstant(), ZONE);
    given(clock.instant()).willReturn(fixedClock.instant());
    given(clock.getZone()).willReturn(ZONE);
  }

  private void setupBusinessDaySequence(LocalDate targetDate, int targetN) {
    var first = targetDate.withDayOfMonth(1);
    given(publicHolidays.isWorkingDay(first)).willReturn(true);
    var current = first;
    for (int i = 1; i < targetN; i++) {
      var next = current.plusDays(1);
      given(publicHolidays.nextWorkingDay(current)).willReturn(next);
      current = next;
    }
  }
}
