package ee.tuleva.onboarding.savings;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest;
import java.time.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class SavingFundDeadlinesService {
  private final PublicHolidays publicHolidays;
  private final Clock estonianClock;

  private static final LocalTime CUTOFF_TIME = LocalTime.of(16, 0);
  private static final LocalTime SCREENING_RETRY_CUTOFF_TIME = LocalTime.of(15, 0);

  private LocalDate firstWorkingDayOnOrAfter(LocalDate date) {
    return publicHolidays.nextWorkingDay(date.minusDays(1));
  }

  private boolean missedTodaysCutoff(LocalDate date, LocalTime time, LocalDate firstWorkingDay) {
    return date.equals(firstWorkingDay) && !time.isBefore(CUTOFF_TIME);
  }

  public Instant getCancellationDeadline(RedemptionRequest redemptionRequest) {
    return getCancellationDeadlineFrom(redemptionRequest.getRequestedAt());
  }

  public Instant getCancellationDeadline(SavingFundPayment payment) {
    if (payment.getReceivedBefore() != null) {
      return getCancellationDeadlineFrom(payment.getReceivedBefore());
    }
    return getCancellationDeadlineFrom(payment.getCreatedAt());
  }

  public Instant getScreeningRetryDeadline(RedemptionRequest redemptionRequest) {
    return dealingDay(redemptionRequest.getRequestedAt())
        .atTime(SCREENING_RETRY_CUTOFF_TIME)
        .atZone(estonianClock.getZone())
        .toInstant();
  }

  private Instant getCancellationDeadlineFrom(Instant eventInstant) {
    return dealingDay(eventInstant).atTime(CUTOFF_TIME).atZone(estonianClock.getZone()).toInstant();
  }

  private LocalDate dealingDay(Instant eventInstant) {
    ZonedDateTime zdt = eventInstant.atZone(estonianClock.getZone());
    LocalDate date = zdt.toLocalDate();
    LocalTime time = zdt.toLocalTime();

    LocalDate firstWorkingDay = firstWorkingDayOnOrAfter(date);
    return missedTodaysCutoff(date, time, firstWorkingDay)
        ? publicHolidays.nextWorkingDay(firstWorkingDay)
        : firstWorkingDay;
  }

  public Instant getFulfillmentDeadline(SavingFundPayment payment) {
    Instant cancellationDeadline = getCancellationDeadline(payment);
    return fulfillmentDeadlineFrom(cancellationDeadline);
  }

  public Instant getFulfillmentDeadline(RedemptionRequest redemptionRequest) {
    Instant cancellationDeadline = getCancellationDeadline(redemptionRequest);
    return fulfillmentDeadlineFrom(cancellationDeadline);
  }

  private Instant fulfillmentDeadlineFrom(Instant instant) {
    ZoneId timeZone = estonianClock.getZone();
    ZonedDateTime zdt = instant.atZone(timeZone);
    LocalDate date = zdt.toLocalDate();

    LocalDate nextWorkingDay = publicHolidays.addWorkingDays(date, 1);
    return nextWorkingDay.atTime(CUTOFF_TIME).atZone(timeZone).toInstant();
  }
}
