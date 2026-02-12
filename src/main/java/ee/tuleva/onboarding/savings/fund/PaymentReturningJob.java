package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.TO_BE_RETURNED;
import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.savings.fund.notification.PaymentsReturnedEvent;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentReturningJob {

  private final SavingFundPaymentRepository savingFundPaymentRepository;
  private final PaymentReturningService paymentReturningService;
  private final ApplicationEventPublisher eventPublisher;

  @Scheduled(fixedRateString = "1m")
  @SchedulerLock(name = "PaymentReturningJob_runJob", lockAtMostFor = "50s", lockAtLeastFor = "10s")
  public void runJob() {
    log.info("Running payment returning job");
    List<SavingFundPayment> paymentsToBeReturned =
        savingFundPaymentRepository.findPaymentsWithStatus(TO_BE_RETURNED);
    var successCount = 0;
    var totalAmount = ZERO;
    for (var payment : paymentsToBeReturned) {
      try {
        paymentReturningService.createReturn(payment);
        successCount++;
        totalAmount = totalAmount.add(payment.getAmount());
      } catch (Exception e) {
        log.error("Payment return failed: payment={}", payment, e);
      }
    }
    log.info("Payment returning job completed: payments={}", successCount);

    if (successCount > 0) {
      eventPublisher.publishEvent(new PaymentsReturnedEvent(successCount, totalAmount));
    }
  }
}
