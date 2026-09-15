package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.savings.SavingFundPayment.Status.RECEIVED;
import static ee.tuleva.onboarding.savings.SavingFundPayment.Status.TO_BE_RETURNED;

import ee.tuleva.onboarding.savings.SavingFundPayment;
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
  private static final Duration UNCONFIRMED_THRESHOLD = Duration.ofHours(36);

  private final SavingFundPaymentRepository paymentRepository;
  private final Clock clock;

  @Scheduled(cron = "0 */15 * * * *", zone = "Europe/Tallinn")
  @SchedulerLock(name = "StuckPaymentAlertJob_runJob", lockAtMostFor = "5m", lockAtLeastFor = "1m")
  public void runJob() {
    paymentRepository
        .findStuckPayments(Instant.now(clock).minus(STUCK_THRESHOLD), RECEIVED, TO_BE_RETURNED)
        .forEach(this::alert);
  }

  @Scheduled(cron = "0 5 9 * * *", zone = "Europe/Tallinn")
  @SchedulerLock(
      name = "StuckPaymentAlertJob_reportUnconfirmedPayments",
      lockAtMostFor = "10m",
      lockAtLeastFor = "1m")
  public void reportUnconfirmedPayments() {
    paymentRepository
        .findUnconfirmedPayments(Instant.now(clock).minus(UNCONFIRMED_THRESHOLD))
        .forEach(this::alertUnconfirmed);
  }

  private void alertUnconfirmed(SavingFundPayment payment) {
    log.error(
        "Savings fund payment not confirmed by the bank: paymentId={}, amount={} EUR, createdAt={}",
        payment.getId(),
        payment.getAmount(),
        payment.getCreatedAt());
  }

  private void alert(SavingFundPayment payment) {
    log.error(
        "Savings fund payment stuck: paymentId={}, status={}, amount={} EUR, stuckSince={}",
        payment.getId(),
        payment.getStatus(),
        payment.getAmount(),
        payment.getStatusChangedAt());
  }
}
