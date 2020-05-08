package ee.tuleva.onboarding.mandate.listener;

import ee.tuleva.onboarding.user.User;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
@Getter
public abstract class MandateCreatedEvent {
    private final User user;
    private final Long mandateId;
    private final byte[] signedFile;

    public static MandateCreatedEvent newEvent(User user, Long mandateId, byte[] signedFile, int pillar) {
        if(pillar == 2) {
            return new SecondPillarMandateCreatedEvent(user, mandateId, signedFile);
        } else if(pillar == 3){
            return new ThirdPillarMandateCreatedEvent(user, mandateId, signedFile);
        } else {
            throw new IllegalArgumentException("Event for the pillar type is not available");
        }
    }
}
