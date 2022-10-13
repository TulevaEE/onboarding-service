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
  private final EpisService episService;
  private final UserConversionService conversionService;

  @EventListener
  public void sendEmails(PaymentCreatedEvent event) {
    ContactDetails contactDetails = episService.getContactDetails(event.getUser());
    emailService.sendThirdPillarPaymentSuccessEmail(
        event.getUser(), event.getPayment(), contactDetails, event.getLocale());
    // scheduleThirdPillarSuggestSecondEmail(event, contactDetails);
  }

  private void scheduleThirdPillarSuggestSecondEmail(
      PaymentCreatedEvent event, ContactDetails contactDetails) {
    ConversionResponse conversion = conversionService.getConversion(event.getUser());
    PillarSuggestion pillarSuggestion =
        new PillarSuggestion(3, event.getUser(), contactDetails, conversion);
    if (pillarSuggestion.isSuggestPillar()) {
      emailService.scheduleThirdPillarSuggestSecondEmail(event.getUser(), event.getLocale());
    }
  }
}
