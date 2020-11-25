package ee.tuleva.onboarding.mandate.email;

import ee.tuleva.onboarding.conversion.ConversionResponse;
import ee.tuleva.onboarding.conversion.UserConversionService;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.contact.UserPreferences;
import ee.tuleva.onboarding.mandate.event.SecondPillarAfterMandateSignedEvent;
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
    public void sendEmail(SecondPillarAfterMandateSignedEvent event) {
        ConversionResponse conversion = conversionService.getConversion(event.getUser());
        UserPreferences userPreferences = episService.getContactDetails(event.getUser());
        emailService.sendSecondPillarMandate(
            event.getUser(),
            event.getMandateId(),
            event.getSignedFile(),
            conversion,
            userPreferences,
            event.getLocale()
        );
    }

    @EventListener
    public void sendEmail(ThirdPillarAfterMandateSignedEvent event) {
        UserPreferences contactDetails = episService.getContactDetails(event.getUser());
        ConversionResponse conversion = conversionService.getConversion(event.getUser());
        emailService.sendThirdPillarMandate(
            event.getUser(),
            event.getMandateId(),
            event.getSignedFile(),
            conversion,
            contactDetails,
            event.getLocale()
        );
    }

}
