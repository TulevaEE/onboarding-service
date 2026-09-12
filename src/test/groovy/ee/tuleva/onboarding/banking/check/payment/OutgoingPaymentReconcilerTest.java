package ee.tuleva.onboarding.banking.check.payment;

import static ee.tuleva.onboarding.banking.check.payment.PaymentCheckSeverity.WARNING;
import static ee.tuleva.onboarding.banking.check.payment.PaymentCheckType.PAYMENT_INDETERMINATE;
import static ee.tuleva.onboarding.banking.check.payment.PaymentCheckType.PAYMENT_NOT_EXECUTED;
import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentStatus.ATTEMPTED;
import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentStatus.SUBMITTED;
import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentType.PAYOUT;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import ee.tuleva.onboarding.banking.payment.OutgoingPayment;
import ee.tuleva.onboarding.banking.payment.OutgoingPaymentRepository;
import ee.tuleva.onboarding.banking.payment.OutgoingPaymentStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutgoingPaymentReconcilerTest {

  private static final Instant NOW = Instant.parse("2026-04-10T12:00:00Z");
  private static final Duration EXECUTION_DEADLINE = Duration.ofHours(24);
  private static final Duration IN_FLIGHT_GRACE = Duration.ofMinutes(15);

  @Mock OutgoingPaymentRepository outgoingPaymentRepository;
  @Mock PaymentCheckService paymentCheckService;

  private final Clock clock = Clock.fixed(NOW, ZoneId.of("UTC"));

  private OutgoingPaymentReconciler reconciler() {
    return new OutgoingPaymentReconciler(
        outgoingPaymentRepository, paymentCheckService, clock, EXECUTION_DEADLINE, IN_FLIGHT_GRACE);
  }

  // A payment we submitted that the bank never executed is usually nobody having approved it.
  @Test
  void aSubmittedPaymentPastItsDeadlineIsReportedAsNotExecuted() {
    givenOverdue(SUBMITTED, List.of(payment("E2E-NOT-EXECUTED", SUBMITTED)));

    reconciler().reconcile();

    then(paymentCheckService)
        .should()
        .record(eq(PAYMENT_NOT_EXECUTED), eq(WARNING), eq("E2E-NOT-EXECUTED"), anyString());
  }

  // Worse than unexecuted: the call never returned a verdict, so the payment may or may not have
  // reached the bank, and nobody may resend it without checking.
  @Test
  void anAttemptedPaymentPastTheGraceIsReportedAsIndeterminate() {
    givenOverdue(ATTEMPTED, List.of(payment("E2E-IN-FLIGHT", ATTEMPTED)));

    reconciler().reconcile();

    then(paymentCheckService)
        .should()
        .record(eq(PAYMENT_INDETERMINATE), eq(WARNING), eq("E2E-IN-FLIGHT"), anyString());
  }

  // The two statuses get different windows on purpose: a submitted payment has all day to execute,
  // an in-flight one is alarming within minutes.
  @Test
  void eachStatusIsMeasuredAgainstItsOwnWindow() {
    reconciler().reconcile();

    then(outgoingPaymentRepository)
        .should()
        .findByStatusAndAttemptedAtBefore(SUBMITTED, NOW.minus(EXECUTION_DEADLINE));
    then(outgoingPaymentRepository)
        .should()
        .findByStatusAndAttemptedAtBefore(ATTEMPTED, NOW.minus(IN_FLIGHT_GRACE));
  }

  @Test
  void nothingOverdueReportsNothing() {
    reconciler().reconcile();

    then(paymentCheckService).should(never()).record(any(), any(), anyString(), anyString());
  }

  /** Both queries are stubbed every time: reconcile() always makes both, whatever is overdue. */
  private void givenOverdue(OutgoingPaymentStatus overdueStatus, List<OutgoingPayment> overdue) {
    given(
            outgoingPaymentRepository.findByStatusAndAttemptedAtBefore(
                SUBMITTED, NOW.minus(EXECUTION_DEADLINE)))
        .willReturn(overdueStatus == SUBMITTED ? overdue : List.of());
    given(
            outgoingPaymentRepository.findByStatusAndAttemptedAtBefore(
                ATTEMPTED, NOW.minus(IN_FLIGHT_GRACE)))
        .willReturn(overdueStatus == ATTEMPTED ? overdue : List.of());
  }

  private static OutgoingPayment payment(String endToEndId, OutgoingPaymentStatus status) {
    return OutgoingPayment.builder()
        .endToEndId(endToEndId)
        .paymentType(PAYOUT)
        .remitterIban("EE111111111111111111")
        .beneficiaryIban("EE222222222222222222")
        .amount(new BigDecimal("100.00"))
        .currency("EUR")
        .bodyHash("hash")
        .status(status)
        .attemptedAt(NOW.minus(Duration.ofDays(2)))
        .build();
  }
}
