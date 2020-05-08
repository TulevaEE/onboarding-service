package ee.tuleva.onboarding.mandate.listener;

import ee.tuleva.onboarding.user.User;

public class SecondPillarMandateCreatedEvent extends MandateCreatedEvent {
    public SecondPillarMandateCreatedEvent(User user, Long mandateId, byte[] signedFile) {
        super(user, mandateId, signedFile);
    }
}
