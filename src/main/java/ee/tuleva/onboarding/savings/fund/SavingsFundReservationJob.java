package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.VERIFIED;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class SavingsFundReservationJob {

  private final SavingFundPaymentRepository savingFundPaymentRepository;
  private final PaymentReservationService paymentReservationService;
  private final PaymentReservationFilterService paymentReservationFilterService;

  @Scheduled(fixedRateString = "1m")
  public void runJob() {
    log.info("Running Savings Fund Reservation Job");
    var verifiedPayments = savingFundPaymentRepository.findPaymentsWithStatus(VERIFIED);
    var paymentsToReserve =
        paymentReservationFilterService.filterPaymentsToReserve(verifiedPayments);
    log.info("Found {} payments to be reserved", paymentsToReserve.size());

    paymentsToReserve.forEach(
        payment -> {
          try {
            paymentReservationService.process(payment);
          } catch (Exception e) {
            log.error("Reservation failed for payment {}", payment, e);
          }
        });
  }
}
