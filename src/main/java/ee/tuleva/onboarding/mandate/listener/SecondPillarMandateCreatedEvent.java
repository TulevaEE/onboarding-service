package ee.tuleva.onboarding.mandate.listener;

import ee.tuleva.onboarding.user.User;

import java.util.Locale;

public class SecondPillarMandateCreatedEvent extends MandateCreatedEvent {
    public SecondPillarMandateCreatedEvent(User user, Long mandateId, byte[] signedFile, Locale locale) {
        super(user, mandateId, signedFile, locale);
    }
}
