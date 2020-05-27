package ee.tuleva.onboarding.mandate.listener;

import ee.tuleva.onboarding.user.User;

import java.util.Locale;

public class ThirdPillarMandateCreatedEvent extends MandateCreatedEvent {
    public ThirdPillarMandateCreatedEvent(User user, Long mandateId, byte[] signedFile, Locale locale) {
        super(user, mandateId, signedFile, locale);
    }
}
