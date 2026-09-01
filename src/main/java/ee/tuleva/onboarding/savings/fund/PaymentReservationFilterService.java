package ee.tuleva.onboarding.savings.fund;

import static java.util.Objects.requireNonNull;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.savings.SavingFundPayment;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentReservationFilterService {

  private final Clock clock;
  private final PublicHolidays publicHolidays;

  public List<SavingFundPayment> filterPaymentsToReserve(List<SavingFundPayment> payments) {
    var cutoffTime = getCutoffTime(clock.instant());
    var previousCutoffTime = getCutoffTime(cutoffTime.minusSeconds(1));

    var paymentsToReserve =
        payments.stream()
            .filter(
                payment -> {
                  var receivedBefore = payment.getReceivedBefore();
                  return receivedBefore != null
                      && receivedBefore.isBefore(cutoffTime)
                      && payment.getCancelledAt() == null;
                })
            .toList();

    paymentsToReserve.forEach(
        payment -> {
          var receivedBefore =
              requireNonNull(
                  payment.getReceivedBefore(),
                  "Missing receivedBefore: paymentId=" + payment.getId());
          if (receivedBefore.isBefore(previousCutoffTime)) {
            log.error(
                "Old payment detected: payment {} was received at {} which is before the previous cutoff time {}",
                payment.getId(),
                receivedBefore,
                cutoffTime);
          }
        });

    return paymentsToReserve;
  }

  private Instant getCutoffTime(Instant now) {
    var estonianZone = ZoneId.of("Europe/Tallinn");
    var nowZoned = now.atZone(estonianZone);
    var today = nowZoned.toLocalDate();

    // If today is a working day and it's after 16:00, use today at 16:00
    if (publicHolidays.isWorkingDay(today) && nowZoned.getHour() >= 16) {
      return today.atTime(16, 0).atZone(estonianZone).toInstant();
    }

    // Otherwise, use previous working day at 16:00
    var previousWorkingDay = publicHolidays.previousWorkingDay(today);
    return previousWorkingDay.atTime(16, 0).atZone(estonianZone).toInstant();
  }
}
