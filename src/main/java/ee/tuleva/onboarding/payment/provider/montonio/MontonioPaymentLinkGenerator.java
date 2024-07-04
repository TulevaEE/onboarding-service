package ee.tuleva.onboarding.payment.provider.montonio;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.payment.PaymentData;
import ee.tuleva.onboarding.payment.PaymentLink;
import ee.tuleva.onboarding.payment.PaymentLinkGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class MontonioPaymentLinkGenerator implements PaymentLinkGenerator {

  private final MontonioOrderCreator montonioOrderCreator;
  private final MontonioOrderClient montonioOrderClient;

  @Override
  public PaymentLink getPaymentLink(PaymentData paymentData, Person person) {
    var order = montonioOrderCreator.getOrder(paymentData, person);
    var url = montonioOrderClient.getPaymentUrl(order, paymentData);
    return new PaymentLink(url);
  }
}
