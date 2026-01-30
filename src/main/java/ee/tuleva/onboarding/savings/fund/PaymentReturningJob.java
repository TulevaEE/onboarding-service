package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.TO_BE_RETURNED;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentReturningJob {

  private final SavingFundPaymentRepository savingFundPaymentRepository;
  private final PaymentReturningService paymentReturningService;

  @Scheduled(fixedRateString = "1m")
  @SchedulerLock(name = "PaymentReturningJob_runJob", lockAtMostFor = "50s", lockAtLeastFor = "10s")
  public void runJob() {
    log.info("Running payment returning job");
    List<SavingFundPayment> paymentsToBeReturned =
        savingFundPaymentRepository.findPaymentsWithStatus(TO_BE_RETURNED);
    paymentsToBeReturned.forEach(
        payment -> {
          try {
            paymentReturningService.createReturn(payment);
          } catch (Exception e) {
            log.error("Payment return failed: payment={}", payment, e);
          }
        });
    log.info("Payment returning job completed: payments={}", paymentsToBeReturned.size());
  }
}
