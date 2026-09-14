package ee.tuleva.onboarding.banking.check.payment;

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

@Component
@RequiredArgsConstructor
public class PaymentCheckEventRecorder {
  private final PaymentCheckService paymentCheckService;

  @EventListener
  public void onPaymentBlocked(PaymentBlockedEvent event) {
    paymentCheckService.recordStoppedPayment(
        PAYMENT_BLOCKED,
        event.paymentRequest().endToEndId(),
        "file does not match the request: "
            + event.violations().stream().map(PaymentIntegrityViolation::summary).toList());
  }

  @EventListener
  public void onPaymentMisrouted(PaymentMisroutedEvent event) {
    paymentCheckService.recordStoppedPayment(
        PAYMENT_MISROUTED,
        event.paymentRequest().endToEndId(),
        "remitter is not one of our bank accounts, so no bank received it");
  }

  @EventListener
  public void onPaymentRejected(PaymentRejectedEvent event) {
    paymentCheckService.recordStoppedPayment(
        PAYMENT_REJECTED,
        event.endToEndId(),
        "the bank rejected it, reasonCode=" + event.reasonCode());
  }
}
