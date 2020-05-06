package ee.tuleva.onboarding.mandate.listener;

import ee.tuleva.onboarding.user.User;

public class ThirdPillarMandateCreatedEvent extends MandateCreatedEvent {
    public ThirdPillarMandateCreatedEvent(User user, Long mandateId, byte[] signedFile) {
        super(user, mandateId, signedFile);
    }
}
