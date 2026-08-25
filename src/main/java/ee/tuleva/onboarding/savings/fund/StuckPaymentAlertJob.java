package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.banking.check.payment.PaymentCheckSeverity.WARNING;
import static ee.tuleva.onboarding.banking.check.payment.PaymentCheckType.PAYMENT_STUCK;
import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.RECEIVED;
import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.TO_BE_RETURNED;

import ee.tuleva.onboarding.banking.check.payment.PaymentCheckService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.jspecify.annotations.NullMarked;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
@NullMarked
public class StuckPaymentAlertJob {

  private static final Duration STUCK_THRESHOLD = Duration.ofMinutes(30);

  private final SavingFundPaymentRepository paymentRepository;
  private final PaymentCheckService paymentCheckService;
  private final Clock clock;

  @Scheduled(cron = "0 */15 * * * *", zone = "Europe/Tallinn")
  @SchedulerLock(name = "StuckPaymentAlertJob_runJob", lockAtMostFor = "5m", lockAtLeastFor = "1m")
  public void runJob() {
    paymentRepository
        .findStuckPayments(Instant.now(clock).minus(STUCK_THRESHOLD), RECEIVED, TO_BE_RETURNED)
        .forEach(this::alert);
  }

  private void alert(SavingFundPayment payment) {
    log.error(
        "Savings fund payment stuck: paymentId={}, status={}, amount={} EUR, stuckSince={}",
        payment.getId(),
        payment.getStatus(),
        payment.getAmount(),
        payment.getStatusChangedAt());
    // The class has been called an AlertJob since it was written; until now it only logged.
    paymentCheckService.record(
        PAYMENT_STUCK,
        WARNING,
        String.valueOf(payment.getId()),
        "an inbound payment has been in %s since %s"
            .formatted(payment.getStatus(), payment.getStatusChangedAt()));
  }
}
