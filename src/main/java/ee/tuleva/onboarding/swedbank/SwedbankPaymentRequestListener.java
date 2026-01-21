package ee.tuleva.onboarding.swedbank;

import ee.tuleva.onboarding.banking.payment.RequestPaymentEvent;
import ee.tuleva.onboarding.swedbank.fetcher.SwedbankAccountConfiguration;
import ee.tuleva.onboarding.swedbank.http.SwedbankGatewayClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;

@RequiredArgsConstructor
@Slf4j
public class SwedbankPaymentRequestListener {

  private final SwedbankGatewayClient swedbankGatewayClient;
  private final SwedbankAccountConfiguration swedbankAccountConfiguration;

  @EventListener
  public void onRequestPayment(RequestPaymentEvent event) {
    String remitterIban = event.paymentRequest().remitterIban();
    if (swedbankAccountConfiguration.getAccountType(remitterIban) == null) {
      log.debug(
          "Ignoring payment request, remitter IBAN is not a Swedbank account: {}", remitterIban);
      return;
    }
    swedbankGatewayClient.sendPaymentRequest(event.paymentRequest(), event.requestId());
  }
}
