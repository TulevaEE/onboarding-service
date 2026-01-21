package ee.tuleva.onboarding.banking.seb;

import ee.tuleva.onboarding.banking.payment.PaymentMessageGenerator;
import ee.tuleva.onboarding.banking.payment.RequestPaymentEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "seb-gateway", name = "enabled", havingValue = "true")
public class SebPaymentRequestListener {

  private final SebGatewayClient sebGatewayClient;
  private final SebAccountConfiguration sebAccountConfiguration;
  private final PaymentMessageGenerator paymentMessageGenerator;

  @EventListener
  public void onRequestPayment(RequestPaymentEvent event) {
    String remitterIban = event.paymentRequest().remitterIban();
    if (sebAccountConfiguration.getAccountType(remitterIban) == null) {
      log.debug("Ignoring payment request, remitter IBAN is not a SEB account: {}", remitterIban);
      return;
    }

    var paymentXml =
        paymentMessageGenerator.generatePaymentMessage(event.paymentRequest(), Seb.BIC);
    sebGatewayClient.submitPaymentFile(paymentXml, event.paymentRequest().endToEndId());
  }
}
