package ee.tuleva.onboarding.savings.fund;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import java.time.*;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class SavingFundPaymentDeadlinesService {
  private final PublicHolidays publicHolidays;
  private final Clock estonianClock;

  private static final LocalTime CANCELLATION_DEADLINE_TIME = LocalTime.of(15, 59);

  public Instant getCancellationDeadline(SavingFundPayment payment) {
    ZoneId timeZone = estonianClock.getZone();
    Instant paymentCreatedAt = payment.getCreatedAt();
    ZonedDateTime paymentCreatedDateTime = paymentCreatedAt.atZone(timeZone);
    LocalDate paymentCreatedDate = paymentCreatedDateTime.toLocalDate();

    // Check if payment was created on a working day before 15:59
    var nextWorkingDay = publicHolidays.nextWorkingDay(paymentCreatedDate.minusDays(1));

    if (nextWorkingDay.equals(paymentCreatedDate)
        && paymentCreatedDateTime.toLocalTime().isBefore(CANCELLATION_DEADLINE_TIME)) {
      // Payment created on working day before 15:59 - deadline is same day at 15:59
      return paymentCreatedDate.atTime(CANCELLATION_DEADLINE_TIME).atZone(timeZone).toInstant();
    }

    // Otherwise, deadline is next working day after payment creation at 15:59
    nextWorkingDay = publicHolidays.nextWorkingDay(paymentCreatedDate);
    return nextWorkingDay.atTime(CANCELLATION_DEADLINE_TIME).atZone(timeZone).toInstant();
  }

  public Instant getFulfillmentDeadline(SavingFundPayment payment) {
    // TODO: Implement
    return payment.getCreatedAt().plus(1, ChronoUnit.DAYS);
  }
}
