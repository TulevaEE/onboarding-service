package ee.tuleva.onboarding.payment;

import static ee.tuleva.onboarding.payment.PaymentData.PaymentChannel.PARTNER;

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
    if (PARTNER == paymentData.getPaymentChannel()) {
      // same logic as in RecurringPaymentService
      return recurringPaymentService.getPaymentLink(paymentData, person);
    }
    return paymentProviderService.getPaymentLink(paymentData, person);
  }
}
