package ee.tuleva.onboarding.banking.seb;

import ee.tuleva.onboarding.banking.payment.PaymentMessageGenerator;
import ee.tuleva.onboarding.banking.payment.RequestPaymentEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;

@RequiredArgsConstructor
@Slf4j
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
