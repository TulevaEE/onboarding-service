package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.RECEIVED;
import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.TO_BE_RETURNED;

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
  }
}
