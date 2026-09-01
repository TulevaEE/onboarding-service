package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.savings.SavingFundPayment.Status.TO_BE_RETURNED;
import static ee.tuleva.onboarding.savings.SavingFundPayment.Status.VERIFIED;

import ee.tuleva.onboarding.savings.SavingFundDeadlinesService;
import ee.tuleva.onboarding.savings.SavingFundPayment;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Slf4j
@RequiredArgsConstructor
public class CancellationJob {

  private final SavingFundPaymentRepository paymentRepository;
  private final SavingFundDeadlinesService deadlinesService;
  private final TransactionTemplate transactionTemplate;
  private final Clock clock;

  @Scheduled(cron = "0 1 16-20 * * *", zone = "Europe/Tallinn")
  @SchedulerLock(name = "CancellationJob_runJob", lockAtMostFor = "50s", lockAtLeastFor = "10s")
  public void runJob() {
    var payments = paymentRepository.findPaymentsWithStatus(VERIFIED);
    for (SavingFundPayment payment : payments) {
      try {
        transactionTemplate.executeWithoutResult(ignored -> process(payment));
      } catch (Exception e) {
        log.warn("Error processing payment {}", payment.getId(), e);
      }
    }
  }

  private void process(SavingFundPayment payment) {
    if (payment.getCancelledAt() == null) return;
    var cancellationDeadline = deadlinesService.getCancellationDeadline(payment);
    if (cancellationDeadline.isAfter(clock.instant())) return;
    log.info("Completing payment cancellation for {}", payment.getId());
    paymentRepository.changeStatus(payment.getId(), TO_BE_RETURNED);
    paymentRepository.addReturnReason(payment.getId(), "Kasutaja soovil");
  }
}
