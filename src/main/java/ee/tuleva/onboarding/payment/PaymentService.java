package ee.tuleva.onboarding.payment;

import static ee.tuleva.onboarding.event.TrackableEventType.PAYMENT_LINK;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.event.annotation.Trackable;
import ee.tuleva.onboarding.payment.provider.PaymentProviderCallbackService;
import ee.tuleva.onboarding.payment.provider.PaymentProviderService;
import ee.tuleva.onboarding.payment.recurring.RecurringPaymentService;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class PaymentService {

  private final PaymentRepository paymentRepository;
  private final PaymentProviderService paymentProviderService;
  private final RecurringPaymentService recurringPaymentService;
  private final PaymentProviderCallbackService paymentProviderCallbackService;

  public List<Payment> getPayments(Person person) {
    return paymentRepository.findAllByRecipientPersonalCode(person.getPersonalCode());
  }

  @Trackable(PAYMENT_LINK)
  PaymentLink getLink(PaymentData paymentData, Person person) {
    return switch (paymentData.getType()) {
      case SINGLE, GIFT -> paymentProviderService.getPaymentLink(paymentData, person);
      case RECURRING -> recurringPaymentService.getPaymentLink(paymentData, person);
    };
  }

  Optional<Payment> processToken(String serializedToken) {
    return paymentProviderCallbackService.processToken(serializedToken);
  }
}
