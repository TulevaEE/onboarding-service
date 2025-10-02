package ee.tuleva.onboarding.savings.fund;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentReturningJob {

  private final SavingFundPaymentRepository savingFundPaymentRepository;
  private final PaymentReturningService paymentReturningService;

  @Scheduled(fixedRateString = "1m")
  public void runJob() {
    savingFundPaymentRepository
        .findPaymentsWithStatus(SavingFundPayment.Status.TO_BE_RETURNED)
        .forEach(
            payment -> {
              try {
                paymentReturningService.createReturn(payment);
              } catch (Exception e) {
                log.error("Identity check failed for payment {}", payment, e);
              }
            });
  }
}
