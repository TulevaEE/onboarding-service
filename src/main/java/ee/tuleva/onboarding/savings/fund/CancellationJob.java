package ee.tuleva.onboarding.savings.fund;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Slf4j
@RequiredArgsConstructor
public class CancellationJob {

  private final SavingFundPaymentRepository paymentRepository;
  private final SavingFundPaymentDeadlinesService deadlinesService;
  private final TransactionTemplate transactionTemplate;

  @Scheduled(cron = "0 1 16-20 * * *", zone = "Europe/Tallinn")
  public void runJob() {
    var payments = paymentRepository.findPaymentsWithStatus(SavingFundPayment.Status.VERIFIED);
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
    if (cancellationDeadline.isAfter(Instant.now())) return;
    log.info("Completing payment cancellation for {}", payment.getId());
    paymentRepository.changeStatus(payment.getId(), SavingFundPayment.Status.TO_BE_RETURNED);
    paymentRepository.addReturnReason(payment.getId(), "Kasutaja soovil");
  }
}
