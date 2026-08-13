package ee.tuleva.onboarding.banking.seb;

import ee.tuleva.onboarding.banking.payment.PaymentBlockedEvent;
import ee.tuleva.onboarding.banking.payment.PaymentFileIntegrityValidator;
import ee.tuleva.onboarding.banking.payment.PaymentIntegrityException;
import ee.tuleva.onboarding.banking.payment.PaymentIntegrityViolation;
import ee.tuleva.onboarding.banking.payment.PaymentMessageGenerator;
import ee.tuleva.onboarding.banking.payment.PaymentMisroutedEvent;
import ee.tuleva.onboarding.banking.payment.RequestPaymentEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;

@RequiredArgsConstructor
@Slf4j
public class SebPaymentRequestListener {

  private final SebGatewayClient sebGatewayClient;
  private final SebAccountConfiguration sebAccountConfiguration;
  private final PaymentMessageGenerator paymentMessageGenerator;
  private final PaymentFileIntegrityValidator paymentFileIntegrityValidator;
  private final ApplicationEventPublisher eventPublisher;

  @EventListener
  public void onRequestPayment(RequestPaymentEvent event) {
    var paymentRequest = event.paymentRequest();

    if (sebAccountConfiguration.getAccountType(paymentRequest.remitterIban()) == null) {
      log.error(
          "Payment request dropped, remitter IBAN is not a SEB account: endToEndId={}",
          paymentRequest.endToEndId());
      eventPublisher.publishEvent(new PaymentMisroutedEvent(paymentRequest));
      return;
    }

    var paymentXml = paymentMessageGenerator.generatePaymentMessage(paymentRequest, Seb.BIC);

    var violations = paymentFileIntegrityValidator.validate(paymentXml, paymentRequest);
    if (!violations.isEmpty()) {
      log.error(
          "Payment file failed integrity validation, not submitting: endToEndId={}, checks={}",
          paymentRequest.endToEndId(),
          violations.stream().map(PaymentIntegrityViolation::summary).toList());
      eventPublisher.publishEvent(new PaymentBlockedEvent(paymentRequest, violations));
      throw new PaymentIntegrityException(paymentRequest.endToEndId(), violations);
    }

    sebGatewayClient.submitPaymentFile(paymentXml, paymentRequest.endToEndId());
  }
}
