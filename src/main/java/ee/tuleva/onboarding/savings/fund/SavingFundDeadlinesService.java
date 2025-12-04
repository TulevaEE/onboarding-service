package ee.tuleva.onboarding.savings.fund;

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

  private LocalDate firstWorkingDayOnOrAfter(LocalDate date) {
    return publicHolidays.nextWorkingDay(date.minusDays(1));
  }

  private boolean isBeforeCutoffOnWorkingDay(LocalDate date, LocalTime time) {
    LocalDate firstWorkingDay = firstWorkingDayOnOrAfter(date);
    return date.equals(firstWorkingDay) && time.isBefore(CUTOFF_TIME);
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

  private Instant getCancellationDeadlineFrom(Instant eventInstant) {
    ZoneId zone = estonianClock.getZone();
    ZonedDateTime zdt = eventInstant.atZone(zone);
    LocalDate date = zdt.toLocalDate();
    LocalTime time = zdt.toLocalTime();

    LocalDate firstWorkingDay = firstWorkingDayOnOrAfter(date);
    LocalDate deadlineDate =
        isBeforeCutoffOnWorkingDay(date, time)
            ? firstWorkingDay
            : publicHolidays.nextWorkingDay(firstWorkingDay);

    return deadlineDate.atTime(CUTOFF_TIME).atZone(zone).toInstant();
  }

  public Instant getFulfillmentDeadline(SavingFundPayment payment) {
    Instant cancellationDeadline = getCancellationDeadline(payment);
    return nextWorkingDayFrom(cancellationDeadline);
  }

  public Instant getFulfillmentDeadline(RedemptionRequest redemptionRequest) {
    Instant cancellationDeadline = getCancellationDeadline(redemptionRequest);
    return nextWorkingDayFrom(cancellationDeadline);
  }

  private Instant nextWorkingDayFrom(Instant instant) {
    ZoneId timeZone = estonianClock.getZone();
    ZonedDateTime zdt = instant.atZone(timeZone);
    LocalDate date = zdt.toLocalDate();

    LocalDate nextWorkingDay = publicHolidays.nextWorkingDay(date);
    return nextWorkingDay.atTime(CUTOFF_TIME).atZone(timeZone).toInstant();
  }
}
