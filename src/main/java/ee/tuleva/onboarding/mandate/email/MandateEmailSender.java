package ee.tuleva.onboarding.mandate.email;

import ee.tuleva.onboarding.conversion.ConversionResponse;
import ee.tuleva.onboarding.conversion.UserConversionService;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.contact.UserPreferences;
import ee.tuleva.onboarding.mandate.event.AfterMandateSignedEvent;
import ee.tuleva.onboarding.mandate.event.ThirdPillarAfterMandateSignedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MandateEmailSender {
  private final MandateEmailService emailService;
  private final EpisService episService;
  private final UserConversionService conversionService;

  @EventListener
  public void sendEmail(AfterMandateSignedEvent event) {
    UserPreferences contactDetails = episService.getContactDetails(event.getUser());
    PillarSuggestion pillarSuggestion = buildPillarSuggestion(event, contactDetails);
    emailService.sendMandate(
        event.getUser(), event.getMandate(), pillarSuggestion, contactDetails, event.getLocale());
  }

  private PillarSuggestion buildPillarSuggestion(
      AfterMandateSignedEvent event, UserPreferences contactDetails) {
    ConversionResponse conversion = conversionService.getConversion(event.getUser());
    return new PillarSuggestion(
        event instanceof ThirdPillarAfterMandateSignedEvent ? 2 : 3,
        event.getUser(),
        contactDetails,
        conversion);
  }
}
