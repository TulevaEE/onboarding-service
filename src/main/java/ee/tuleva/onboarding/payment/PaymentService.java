package ee.tuleva.onboarding.payment;

import static ee.tuleva.onboarding.event.TrackableEventType.PAYMENT_LINK;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.event.annotation.Trackable;
import ee.tuleva.onboarding.payment.provider.PaymentProviderCallbackService;
import ee.tuleva.onboarding.payment.provider.PaymentProviderService;
import ee.tuleva.onboarding.payment.recurring.RecurringPaymentService;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
@Slf4j
public class PaymentService {

  private final PaymentRepository paymentRepository;
  private final PaymentProviderService paymentProviderService;
  private final RecurringPaymentService recurringPaymentService;
  private final PaymentProviderCallbackService paymentProviderCallbackService;
  private final UserService userService;

  public List<Payment> getPayments(Person person) {
    return paymentRepository.findAllByRecipientPersonalCode(person.getPersonalCode());
  }

  @Trackable(PAYMENT_LINK)
  PaymentLink getLink(PaymentData paymentData, Person person) {
    return switch (paymentData.getType()) {
      case SINGLE, GIFT, MEMBER_FEE -> paymentProviderService.getPaymentLink(paymentData, person);
      case RECURRING -> recurringPaymentService.getPaymentLink(paymentData, person);
    };
  }

  Optional<Payment> processToken(String serializedToken) {
    Optional<Payment> paymentOptional =
        paymentProviderCallbackService.processToken(serializedToken);
    paymentOptional.ifPresent(
        payment -> {
          if (payment.getPaymentType() == PaymentData.PaymentType.MEMBER_FEE) {
            registerMemberPayment(payment);
          }
        });

    return paymentOptional;
  }

  private void registerMemberPayment(Payment payment) {
    User user = payment.getUser();
    if (user.isMember()) {
      log.error(
          "Member payment {} for user {}. User already is a member.",
          payment.getId(),
          user.getId());
      return;
    }

    userService.registerAsMember(user.getId());
  }
}
