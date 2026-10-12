package ee.tuleva.onboarding.banking.check.payment;

import static ee.tuleva.onboarding.banking.check.payment.PaymentCheckSeverity.WARNING;
import static ee.tuleva.onboarding.banking.check.payment.PaymentCheckType.PAYMENT_INDETERMINATE;
import static ee.tuleva.onboarding.banking.check.payment.PaymentCheckType.PAYMENT_NOT_EXECUTED;
import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentStatus.ATTEMPTED;
import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentStatus.SUBMITTED;
import static ee.tuleva.onboarding.banking.payment.PaymentApprovalBriefJob.BRIEF_CRON;
import static java.util.Objects.requireNonNull;

import ee.tuleva.onboarding.banking.payment.OutgoingPayment;
import ee.tuleva.onboarding.banking.payment.OutgoingPaymentRepository;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile({"production", "staging"})
public class OutgoingPaymentReconciler {
  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");
  private static final CronExpression BRIEF = CronExpression.parse(BRIEF_CRON);

  private final OutgoingPaymentRepository outgoingPaymentRepository;
  private final PaymentCheckService paymentCheckService;
  private final PublicHolidays publicHolidays;
  private final Clock clock;
  private final Duration inFlightGrace;

  public OutgoingPaymentReconciler(
      OutgoingPaymentRepository outgoingPaymentRepository,
      PaymentCheckService paymentCheckService,
      PublicHolidays publicHolidays,
      Clock clock,
      @Value("${banking.payment.in-flight-grace:PT15M}") Duration inFlightGrace) {
    this.outgoingPaymentRepository = outgoingPaymentRepository;
    this.paymentCheckService = paymentCheckService;
    this.publicHolidays = publicHolidays;
    this.clock = clock;
    this.inFlightGrace = inFlightGrace;
  }

  @Scheduled(cron = "0 0/30 9-18 * * MON-FRI", zone = "Europe/Tallinn")
  @SchedulerLock(name = "OutgoingPaymentReconciler", lockAtMostFor = "10m", lockAtLeastFor = "1m")
  public void reconcile() {
    var now = Instant.now(clock);

    outgoingPaymentRepository
        .findByStatusAndAttemptedAtBefore(SUBMITTED, briefOnThePreviousWorkingDay(now))
        .forEach(this::reportNotExecuted);

    outgoingPaymentRepository
        .findByStatusAndAttemptedAtBefore(ATTEMPTED, now.minus(inFlightGrace))
        .forEach(this::reportIndeterminate);
  }

  private Instant briefOnThePreviousWorkingDay(Instant now) {
    var previousWorkingDay = publicHolidays.previousWorkingDay(now.atZone(TALLINN).toLocalDate());
    return requireNonNull(BRIEF.next(previousWorkingDay.atStartOfDay(TALLINN))).toInstant();
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
