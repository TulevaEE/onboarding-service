package ee.tuleva.onboarding.mandate.event;

import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.user.User;

import java.util.Locale;

public class ThirdPillarMandateCreatedEvent extends MandateCreatedEvent {
    public ThirdPillarMandateCreatedEvent(Object source, User user, Mandate mandate, Locale locale) {
        super(source, user, mandate, locale);
    }
}
