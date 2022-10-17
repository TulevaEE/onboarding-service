package ee.tuleva.onboarding.payment.email;

import ee.tuleva.onboarding.conversion.ConversionResponse;
import ee.tuleva.onboarding.conversion.UserConversionService;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.contact.ContactDetails;
import ee.tuleva.onboarding.mandate.email.PillarSuggestion;
import ee.tuleva.onboarding.payment.event.PaymentCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentEmailSender {

  private final PaymentEmailService emailService;

  @EventListener
  public void sendEmails(PaymentCreatedEvent event) {
    emailService.sendThirdPillarPaymentSuccessEmail(event.getUser(), event.getLocale());
  }
}
