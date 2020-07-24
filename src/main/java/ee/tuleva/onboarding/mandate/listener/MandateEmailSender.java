package ee.tuleva.onboarding.mandate.listener;

import ee.tuleva.onboarding.conversion.ConversionResponse;
import ee.tuleva.onboarding.conversion.UserConversionService;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.contact.UserPreferences;
import ee.tuleva.onboarding.mandate.email.MandateEmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MandateEmailSender {
    private final MandateEmailService emailService;
    private final EpisService episService;
    private final UserConversionService conversionService;

    @Async
    @EventListener
    public void onSecondPillarMandateCreatedEvent(SecondPillarMandateCreatedEvent event) {
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
    public void onThirdPillarMandateCreatedEvent(ThirdPillarMandateCreatedEvent event) {
        ConversionResponse conversion = conversionService.getConversion(event.getUser());
        UserPreferences userPreferences = episService.getContactDetails(event.getUser());
        emailService.sendThirdPillarMandate(
            event.getUser(),
            event.getMandateId(),
            event.getSignedFile(),
            conversion,
            userPreferences,
            event.getLocale()
        );
    }

}
