package ee.tuleva.onboarding.savings.fund;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentVerificationJob {

  private final SavingFundPaymentRepository savingFundPaymentRepository;
  private final PaymentVerificationService paymentVerificationService;

  @Scheduled(fixedRateString = "1m")
  public void runJob() {
    savingFundPaymentRepository
        .findPaymentsWithStatus(SavingFundPayment.Status.RECEIVED)
        .forEach(
            payment -> {
              try {
                paymentVerificationService.process(payment);
              } catch (Exception e) {
                log.error("Identity check failed for payment {}", payment, e);
              }
            });
  }
}
