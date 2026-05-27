package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.check.tracking.PeriodType.MONTHLY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.investment.event.RunTdAttributionBackfillRequested;
import ee.tuleva.onboarding.investment.event.RunTdAttributionMonthlyRequested;
import ee.tuleva.onboarding.investment.event.RunTdAttributionRequested;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PeriodicTdAttributionJobTest {

  @Mock PeriodicTdAttributionService service;
  @Spy PublicHolidays publicHolidays = new PublicHolidays();

  @InjectMocks PeriodicTdAttributionJob job;

  @Test
  void computesOnFourthBusinessDay() {
    // January 2026: 1st = Thursday (holiday), 2nd = Friday (1st bday),
    // 5th = Monday (2nd), 6th = Tuesday (3rd), 7th = Wednesday (4th bday)
    var jan7clock = clockFor("2026-01-07");
    var jobWithClock = new PeriodicTdAttributionJob(service, publicHolidays, jan7clock);

    jobWithClock.computeMonthlyIfReady();

    verify(service)
        .computeForAllFunds(LocalDate.of(2025, 12, 1), LocalDate.of(2025, 12, 31), MONTHLY);
  }

  @Test
  void skipsWhenNotFourthBusinessDay() {
    // January 6, 2026 = 3rd business day, not 4th
    var jan6clock = clockFor("2026-01-06");
    var jobWithClock = new PeriodicTdAttributionJob(service, publicHolidays, jan6clock);

    jobWithClock.computeMonthlyIfReady();

    verify(service, never()).computeForAllFunds(any(), any(), any());
  }

  @Test
  void onAttributionRequestedCallsServiceWithParameters() {
    var event =
        new RunTdAttributionRequested(
            "TUK75", LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30), "MONTHLY");

    var jobWithClock =
        new PeriodicTdAttributionJob(service, publicHolidays, clockFor("2026-05-07"));
    jobWithClock.onAttributionRequested(event);

    verify(service)
        .computeAttribution(TUK75, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30), MONTHLY);
  }

  @Test
  void onMonthlyRequestedComputesLastMonth() {
    var jobWithClock =
        new PeriodicTdAttributionJob(service, publicHolidays, clockFor("2026-05-07"));

    jobWithClock.onMonthlyRequested(new RunTdAttributionMonthlyRequested());

    verify(service)
        .computeForAllFunds(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30), MONTHLY);
  }

  @Test
  void onBackfillRequestedCallsServiceBackfill() {
    var jobWithClock =
        new PeriodicTdAttributionJob(service, publicHolidays, clockFor("2026-05-07"));

    jobWithClock.onBackfillRequested(new RunTdAttributionBackfillRequested(3));

    verify(service).backfillMonths(eq(3), any(Clock.class));
  }

  @Test
  void isNthBusinessDayHandlesWeekendStart() {
    // February 2026: 1st = Sunday → 1st bday = 2nd (Mon), 4th bday = 5th (Thu)
    assertThat(job.isNthBusinessDayOfMonth(LocalDate.of(2026, 2, 5), 4)).isTrue();
    assertThat(job.isNthBusinessDayOfMonth(LocalDate.of(2026, 2, 4), 4)).isFalse();
  }

  @Test
  void isNthBusinessDayHandlesHolidayStart() {
    // May 2026: 1st = Friday (holiday) → 1st bday = 4th (Mon), 4th bday = 7th (Thu)
    assertThat(job.isNthBusinessDayOfMonth(LocalDate.of(2026, 5, 7), 4)).isTrue();
    assertThat(job.isNthBusinessDayOfMonth(LocalDate.of(2026, 5, 6), 4)).isFalse();
  }

  private static Clock clockFor(String date) {
    return Clock.fixed(
        LocalDate.parse(date).atStartOfDay(ZoneId.of("Europe/Tallinn")).toInstant(),
        ZoneId.of("Europe/Tallinn"));
  }
}
