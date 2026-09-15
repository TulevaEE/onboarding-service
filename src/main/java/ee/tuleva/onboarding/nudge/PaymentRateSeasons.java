package ee.tuleva.onboarding.nudge;

import static ee.tuleva.onboarding.nudge.PaymentRateSeason.Mode.CLOSED;
import static ee.tuleva.onboarding.nudge.PaymentRateSeason.Mode.LAST_DAYS;
import static ee.tuleva.onboarding.nudge.PaymentRateSeason.Mode.OFF_SEASON;
import static ee.tuleva.onboarding.nudge.PaymentRateSeason.Mode.SEASON;

import ee.tuleva.onboarding.deadline.MandateDeadlines;
import ee.tuleva.onboarding.deadline.MandateDeadlinesService;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class PaymentRateSeasons {

  private static final int LAST_DAYS_LENGTH = 2;

  private final Clock estonianClock;
  private final MandateDeadlinesService mandateDeadlinesService;

  PaymentRateSeason current() {
    MandateDeadlines deadlines = mandateDeadlinesService.getDeadlines();
    LocalDate deadline =
        deadlines.getPaymentRateDeadline().atZone(estonianClock.getZone()).toLocalDate();
    LocalDate fulfillmentDate = deadlines.getPaymentRateFulfillmentDate();
    return new PaymentRateSeason(
        deadline, fulfillmentDate, mode(LocalDate.now(estonianClock), deadline, fulfillmentDate));
  }

  private static PaymentRateSeason.Mode mode(
      LocalDate today, LocalDate deadline, LocalDate fulfillmentDate) {
    if (fulfillmentDate.getYear() > today.getYear() + 1) {
      return CLOSED;
    }
    if (today.isBefore(deadline.withDayOfMonth(1))) {
      return OFF_SEASON;
    }
    return today.isBefore(deadline.minusDays(LAST_DAYS_LENGTH)) ? SEASON : LAST_DAYS;
  }
}
