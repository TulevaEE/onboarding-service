package ee.tuleva.onboarding.savings.fund;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class SavingFundPaymentCancellationService {
  private final PublicHolidays publicHolidays;
  private final Clock clock;

  private static final LocalTime CANCELLATION_DEADLINE_TIME = LocalTime.of(15, 59);

  Instant getCancellationDeadline(SavingFundPayment payment) {
    var paymentCreatedAt = payment.getCreatedAt();
    var timeZone = clock.getZone();
    var paymentCreatedDateTime = paymentCreatedAt.atZone(timeZone);
    var paymentCreatedDate = paymentCreatedDateTime.toLocalDate();

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

  // TODO: Implement payment cancellation
}
