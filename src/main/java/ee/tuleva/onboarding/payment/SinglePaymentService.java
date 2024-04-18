package ee.tuleva.onboarding.payment;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.payment.provider.PaymentProviderService;
import ee.tuleva.onboarding.payment.recurring.RecurringPaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SinglePaymentService implements PaymentLinkGenerator {

  private final RecurringPaymentService recurringPaymentService;
  private final PaymentProviderService paymentProviderService;

  @Override
  public PaymentLink getPaymentLink(PaymentData paymentData, Person person) {
    return switch (paymentData.getPaymentChannel()) {
        // same logic as in RecurringPaymentService
      case PARTNER, COOP_WEB -> recurringPaymentService.getPaymentLink(paymentData, person);
      default -> paymentProviderService.getPaymentLink(paymentData, person);
    };
  }
}
