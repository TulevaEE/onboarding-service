package ee.tuleva.onboarding.payment;

import static ee.tuleva.onboarding.event.TrackableEventType.PAYMENT_LINK;
import static ee.tuleva.onboarding.payment.PaymentData.PaymentType.MEMBER_FEE;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.event.annotation.Trackable;
import ee.tuleva.onboarding.mandate.event.BeforePaymentLinkCreatedEvent;
import ee.tuleva.onboarding.payment.provider.montonio.MontonioCallbackService;
import ee.tuleva.onboarding.payment.recurring.RecurringPaymentLinkGenerator;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
@Slf4j
public class PaymentService {

  private final PaymentRepository paymentRepository;
  private final SinglePaymentLinkGenerator singlePaymentLinkGenerator;
  private final RecurringPaymentLinkGenerator recurringPaymentLinkGenerator;
  private final MontonioCallbackService montonioCallbackService;
  private final UserService userService;
  private final ApplicationEventPublisher applicationEventPublisher;
  private final EpisService episService;

  public List<Payment> getThirdPillarPayments(Person person) {
    return paymentRepository.findAllByRecipientPersonalCodeAndPaymentTypeNot(
        person.getPersonalCode(), MEMBER_FEE);
  }

  @Trackable(PAYMENT_LINK)
  PaymentLink getLink(PaymentData paymentData, Person person) {
    publishAmlCheckEvent(person, paymentData);
    return switch (paymentData.getType()) {
      case SINGLE, GIFT, MEMBER_FEE ->
          singlePaymentLinkGenerator.getPaymentLink(paymentData, person);
      case RECURRING -> recurringPaymentLinkGenerator.getPaymentLink(paymentData, person);
    };
  }

  Optional<Payment> processToken(String serializedToken) {
    Optional<Payment> paymentOptional = montonioCallbackService.processToken(serializedToken);
    paymentOptional.ifPresent(
        payment -> {
          if (payment.getPaymentType() == MEMBER_FEE) {
            registerMemberPayment(payment);
          }
        });

    return paymentOptional;
  }

  private void publishAmlCheckEvent(Person person, PaymentData paymentData) {
    var user = userService.findByPersonalCode(person.getPersonalCode()).orElseThrow();
    var address = episService.getContactDetails(person).getAddress();

    applicationEventPublisher.publishEvent(
        new BeforePaymentLinkCreatedEvent(this, user, address, paymentData));
  }

  private void registerMemberPayment(Payment payment) {
    User user = payment.getUser();
    if (user.isMember()) {
      log.info(
          "Member payment {} for user {}. User already is a member.",
          payment.getId(),
          user.getId());
      return;
    }

    userService.registerAsMember(user.getId());
  }
}
