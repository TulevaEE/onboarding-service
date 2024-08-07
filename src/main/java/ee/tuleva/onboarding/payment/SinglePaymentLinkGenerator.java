package ee.tuleva.onboarding.payment;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.payment.provider.montonio.MontonioPaymentLinkGenerator;
import ee.tuleva.onboarding.payment.recurring.CoopPankPaymentLinkGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SinglePaymentLinkGenerator implements PaymentLinkGenerator {

  private final CoopPankPaymentLinkGenerator coopPankPaymentLinkGenerator;
  private final MontonioPaymentLinkGenerator montonioPaymentLinkGenerator;

  @Override
  public PaymentLink getPaymentLink(PaymentData paymentData, Person person) {
    return switch (paymentData.getPaymentChannel()) {
      case PARTNER, COOP_WEB -> coopPankPaymentLinkGenerator.getPaymentLink(paymentData, person);
      default -> montonioPaymentLinkGenerator.getPaymentLink(paymentData, person);
    };
  }
}
