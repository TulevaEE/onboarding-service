package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.RECEIVED;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentVerificationJob {

  private final SavingFundPaymentRepository savingFundPaymentRepository;
  private final PaymentVerificationService paymentVerificationService;

  // @Scheduled(fixedRateString = "1m")
  @SchedulerLock(
      name = "PaymentVerificationJob_runJob",
      lockAtMostFor = "50s",
      lockAtLeastFor = "10s")
  public void runJob() {
    savingFundPaymentRepository
        .findPaymentsWithStatus(RECEIVED)
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
