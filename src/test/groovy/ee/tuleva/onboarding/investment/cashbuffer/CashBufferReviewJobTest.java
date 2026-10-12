package ee.tuleva.onboarding.investment.cashbuffer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.deadline.BusinessDays;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CashBufferReviewJobTest {

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");

  @Mock private CashBufferReviewService service;
  @Spy private BusinessDays businessDays = new BusinessDays(new PublicHolidays());
  @Mock private Clock clock;

  @InjectMocks private CashBufferReviewJob job;

  @Test
  void reviewsTheClosedMonthOnTheFourthBusinessDay() {
    today(LocalDate.of(2026, 10, 6));

    job.reviewTheClosedMonthIfDue();

    verify(service).reviewAllFunds(YearMonth.of(2026, 9), LocalDate.of(2026, 10, 6));
    verify(service, never()).reviewTheFundsStillWithoutAReview(any(), any());
  }

  @Test
  void countsBusinessDaysPastTheSpringDayHolidayAndTheWeekend() {
    today(LocalDate.of(2026, 5, 7));

    job.reviewTheClosedMonthIfDue();

    verify(service).reviewAllFunds(YearMonth.of(2026, 4), LocalDate.of(2026, 5, 7));
  }

  @Test
  void retriesTheFundsStillWithoutAReviewOnTheDaysAfterTheFourthBusinessDay() {
    today(LocalDate.of(2026, 10, 7));

    job.reviewTheClosedMonthIfDue();

    verify(service)
        .reviewTheFundsStillWithoutAReview(YearMonth.of(2026, 9), LocalDate.of(2026, 10, 7));
    verify(service, never()).reviewAllFunds(any(), any());
  }

  @Test
  void doesNothingBeforeTheFourthBusinessDay() {
    today(LocalDate.of(2026, 10, 5));

    job.reviewTheClosedMonthIfDue();

    verify(service, never()).reviewAllFunds(any(), any());
    verify(service, never()).reviewTheFundsStillWithoutAReview(any(), any());
  }

  private void today(LocalDate date) {
    var fixed = Clock.fixed(date.atTime(7, 30).atZone(TALLINN).toInstant(), TALLINN);
    given(clock.instant()).willReturn(fixed.instant());
    given(clock.getZone()).willReturn(TALLINN);
  }
}
