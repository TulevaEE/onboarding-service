package ee.tuleva.onboarding.mandate.listener;

import ee.tuleva.onboarding.user.User;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public abstract class MandateCreatedEvent {
    public static MandateCreatedEvent newEvent(User user, Long mandateId, byte[] signedFile, int pillar) {
        Map<Integer, Class<? extends MandateCreatedEvent>> eventTypes =
            new HashMap<Integer, Class<? extends MandateCreatedEvent>>() {
                {
                    put(2, SecondPillarMandateCreatedEvent.class);
                    put(3, ThirdPillarMandateCreatedEvent.class);
                }
            };
        try{
            return eventTypes.get(pillar).getConstructor(User.class, Long.class, byte[].class).newInstance(
                user, mandateId, signedFile
            );
        } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
            log.debug("Can't create event", e);
            return null;
        }
    }
}
