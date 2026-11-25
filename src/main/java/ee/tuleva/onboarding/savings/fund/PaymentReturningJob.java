package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.banking.check.payment.PaymentCheckType.RETURN_BLOCKED;
import static ee.tuleva.onboarding.savings.SavingFundPayment.Status.FROZEN;
import static ee.tuleva.onboarding.savings.SavingFundPayment.Status.TO_BE_RETURNED;

import ee.tuleva.onboarding.banking.check.payment.PaymentCheckService;
import ee.tuleva.onboarding.savings.SavingFundPayment;
import ee.tuleva.onboarding.savings.fund.notification.SanctionedPaymentFrozenEvent;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentReturningJob {

  private final SavingFundPaymentRepository savingFundPaymentRepository;
  private final PaymentReturningService paymentReturningService;
  private final PaymentReturnValidator paymentReturnValidator;
  private final PaymentCheckService paymentCheckService;
  private final PayoutAccountPayerScreening payoutAccountPayerScreening;
  private final ApplicationEventPublisher eventPublisher;
  private final TransactionTemplate transactionTemplate;

  @Scheduled(fixedRateString = "1m")
  @SchedulerLock(name = "PaymentReturningJob_runJob", lockAtMostFor = "30m", lockAtLeastFor = "10s")
  public void runJob() {
    log.info("Running payment returning job");
    List<SavingFundPayment> paymentsToBeReturned =
        savingFundPaymentRepository.findPaymentsWithStatus(TO_BE_RETURNED);
    var successCount = 0;
    for (var payment : paymentsToBeReturned) {
      var blockingReason = paymentReturnValidator.findBlockingReason(payment);
      if (blockingReason.isPresent()) {
        block(payment, blockingReason.get());
        continue;
      }
      switch (payoutAccountPayerScreening.screen(payment)) {
        case SANCTION_HIT -> freeze(payment);
        case UNAVAILABLE ->
            block(payment, "Payer sanctions screening is unavailable, try again later");
        case CLEAR, PEP_HIT, NOT_CHECKED -> {
          if (sendReturn(payment)) {
            successCount++;
          }
        }
      }
    }
    log.info("Payment returning job completed: payments={}", successCount);
  }

  private boolean sendReturn(SavingFundPayment payment) {
    try {
      paymentReturningService.createReturn(payment);
      return true;
    } catch (Exception e) {
      log.error("Payment return failed: paymentId={}", payment.getId(), e);
      return false;
    }
  }

  private void freeze(SavingFundPayment payment) {
    log.error(
        "Payer of a payment received on the payout account has a sanctions hit, freezing it: paymentId={}",
        payment.getId());
    transactionTemplate.executeWithoutResult(
        ignored -> {
          savingFundPaymentRepository.changeStatus(payment.getId(), FROZEN);
          eventPublisher.publishEvent(
              new SanctionedPaymentFrozenEvent(payment.getId(), payment.getAmount()));
        });
  }

  private void block(SavingFundPayment payment, String reason) {
    log.error(
        "Payment return blocked, leaving it to be returned: paymentId={}, reason={}",
        payment.getId(),
        reason);
    paymentCheckService.recordStoppedPayment(RETURN_BLOCKED, payment.getId().toString(), reason);
  }
}
