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
    int suggestPillar = event instanceof ThirdPillarAfterMandateSignedEvent ? 2 : 3;
    UserPreferences contactDetails = episService.getContactDetails(event.getUser());
    ConversionResponse conversion = conversionService.getConversion(event.getUser());
    PillarSuggestion pillarSuggestion =
        new PillarSuggestion(suggestPillar, event.getUser(), contactDetails, conversion);
    emailService.sendMandate(
        event.getUser(), event.getMandate(), pillarSuggestion, contactDetails, event.getLocale());
  }
}
