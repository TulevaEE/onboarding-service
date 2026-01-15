package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.TO_BE_RETURNED;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentReturningJob {

  private final SavingFundPaymentRepository savingFundPaymentRepository;
  private final PaymentReturningService paymentReturningService;

  // @Scheduled(fixedRateString = "1m")
  @SchedulerLock(name = "PaymentReturningJob_runJob", lockAtMostFor = "50s", lockAtLeastFor = "10s")
  public void runJob() {
    savingFundPaymentRepository
        .findPaymentsWithStatus(TO_BE_RETURNED)
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
