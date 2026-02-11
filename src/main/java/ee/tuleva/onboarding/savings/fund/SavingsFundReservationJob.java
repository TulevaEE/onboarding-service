package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.VERIFIED;
import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.savings.fund.notification.ReservationCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class SavingsFundReservationJob {

  private final SavingFundPaymentRepository savingFundPaymentRepository;
  private final PaymentReservationService paymentReservationService;
  private final PaymentReservationFilterService paymentReservationFilterService;
  private final ApplicationEventPublisher eventPublisher;

  @Scheduled(fixedRateString = "1m")
  @SchedulerLock(
      name = "SavingsFundReservationJob_runJob",
      lockAtMostFor = "50s",
      lockAtLeastFor = "10s")
  public void runJob() {
    log.info("Running Savings Fund Reservation Job");
    var verifiedPayments = savingFundPaymentRepository.findPaymentsWithStatus(VERIFIED);
    var paymentsToReserve =
        paymentReservationFilterService.filterPaymentsToReserve(verifiedPayments);
    log.info("Found {} payments to be reserved", paymentsToReserve.size());

    var successCount = 0;
    var successAmount = ZERO;
    for (var payment : paymentsToReserve) {
      try {
        paymentReservationService.process(payment);
        successCount++;
        successAmount = successAmount.add(payment.getAmount());
      } catch (Exception e) {
        log.error("Reservation failed for payment {}", payment, e);
      }
    }

    log.info("Payment reservation job completed: processedPayments={}", successCount);

    if (successCount > 0) {
      eventPublisher.publishEvent(new ReservationCompletedEvent(successCount, successAmount));
    }
  }
}
