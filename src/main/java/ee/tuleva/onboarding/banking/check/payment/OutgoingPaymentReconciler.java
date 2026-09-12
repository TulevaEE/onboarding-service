package ee.tuleva.onboarding.banking.check.payment;

import static ee.tuleva.onboarding.banking.check.payment.PaymentCheckSeverity.WARNING;
import static ee.tuleva.onboarding.banking.check.payment.PaymentCheckType.PAYMENT_INDETERMINATE;
import static ee.tuleva.onboarding.banking.check.payment.PaymentCheckType.PAYMENT_NOT_EXECUTED;
import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentStatus.ATTEMPTED;
import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentStatus.SUBMITTED;

import ee.tuleva.onboarding.banking.payment.OutgoingPayment;
import ee.tuleva.onboarding.banking.payment.OutgoingPaymentRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Watches payments whose fate we never learned.
 *
 * <p>Two cases, and they are different problems. A payment we submitted that the bank never
 * executed is usually nobody having approved it — which is the check that catches a forgotten
 * approval. A payment still in flight is worse: the call never returned a verdict, so it may or may
 * not have reached the bank, and nobody may resend it without checking.
 */
@Slf4j
@Component
@Profile({"production", "staging"})
public class OutgoingPaymentReconciler {

  private final OutgoingPaymentRepository outgoingPaymentRepository;
  private final PaymentCheckService paymentCheckService;
  private final Clock clock;
  private final Duration executionDeadline;
  private final Duration inFlightGrace;

  public OutgoingPaymentReconciler(
      OutgoingPaymentRepository outgoingPaymentRepository,
      PaymentCheckService paymentCheckService,
      Clock clock,
      @Value("${banking.payment.execution-deadline:PT24H}") Duration executionDeadline,
      @Value("${banking.payment.in-flight-grace:PT15M}") Duration inFlightGrace) {
    this.outgoingPaymentRepository = outgoingPaymentRepository;
    this.paymentCheckService = paymentCheckService;
    this.clock = clock;
    this.executionDeadline = executionDeadline;
    this.inFlightGrace = inFlightGrace;
  }

  @Scheduled(cron = "0 0/30 9-18 * * MON-FRI", zone = "Europe/Tallinn")
  @SchedulerLock(name = "OutgoingPaymentReconciler", lockAtMostFor = "10m", lockAtLeastFor = "1m")
  public void reconcile() {
    var now = Instant.now(clock);

    outgoingPaymentRepository
        .findByStatusAndAttemptedAtBefore(SUBMITTED, now.minus(executionDeadline))
        .forEach(this::reportNotExecuted);

    outgoingPaymentRepository
        .findByStatusAndAttemptedAtBefore(ATTEMPTED, now.minus(inFlightGrace))
        .forEach(this::reportIndeterminate);
  }

  private void reportNotExecuted(OutgoingPayment payment) {
    paymentCheckService.record(
        PAYMENT_NOT_EXECUTED,
        WARNING,
        payment.getEndToEndId(),
        "submitted %s and still not executed, type=%s"
            .formatted(payment.getAttemptedAt(), payment.getPaymentType()));
  }

  private void reportIndeterminate(OutgoingPayment payment) {
    paymentCheckService.record(
        PAYMENT_INDETERMINATE,
        WARNING,
        payment.getEndToEndId(),
        "the call never returned a verdict, so it may or may not have reached the bank; type=%s"
            .formatted(payment.getPaymentType()));
  }
}
