package ee.tuleva.onboarding.banking.check.payment;

import static ee.tuleva.onboarding.banking.check.payment.PaymentCheckSeverity.HOLD;
import static ee.tuleva.onboarding.banking.check.payment.PaymentCheckType.PAYMENT_BLOCKED;
import static ee.tuleva.onboarding.banking.check.payment.PaymentCheckType.PAYMENT_MISROUTED;
import static ee.tuleva.onboarding.banking.check.payment.PaymentCheckType.PAYMENT_REJECTED;

import ee.tuleva.onboarding.banking.payment.PaymentBlockedEvent;
import ee.tuleva.onboarding.banking.payment.PaymentIntegrityViolation;
import ee.tuleva.onboarding.banking.payment.PaymentMisroutedEvent;
import ee.tuleva.onboarding.banking.payment.PaymentRejectedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Turns a stopped payment into a durable record, which is what lets the approval brief name a hold.
 *
 * <p>A payment blocked by Layer 1 or Layer 2 never gets an outgoing payment row — that write
 * happens after validation — so without this the brief would simply show one fewer payment than
 * expected, which is indistinguishable from a cancellation.
 */
@Component
@RequiredArgsConstructor
public class PaymentCheckEventRecorder {

  private final PaymentCheckService paymentCheckService;

  @EventListener
  public void onPaymentBlocked(PaymentBlockedEvent event) {
    paymentCheckService.record(
        PAYMENT_BLOCKED,
        HOLD,
        event.paymentRequest().endToEndId(),
        "file does not match the request: "
            + event.violations().stream().map(PaymentIntegrityViolation::summary).toList());
  }

  @EventListener
  public void onPaymentMisrouted(PaymentMisroutedEvent event) {
    paymentCheckService.record(
        PAYMENT_MISROUTED,
        HOLD,
        event.paymentRequest().endToEndId(),
        "remitter is not one of our bank accounts, so no bank received it");
  }

  @EventListener
  public void onPaymentRejected(PaymentRejectedEvent event) {
    paymentCheckService.record(
        PAYMENT_REJECTED,
        HOLD,
        event.endToEndId(),
        "the bank rejected it, reasonCode=" + event.reasonCode());
  }
}
