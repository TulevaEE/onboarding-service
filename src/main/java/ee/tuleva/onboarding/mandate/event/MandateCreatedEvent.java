package ee.tuleva.onboarding.mandate.event;

import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.user.User;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.Locale;

@Getter
public abstract class MandateCreatedEvent extends ApplicationEvent {

    private final User user;
    private final Mandate mandate;
    private final Locale locale;

    public MandateCreatedEvent(Object source, User user, Mandate mandate, Locale locale) {
        super(source);
        this.user = user;
        this.mandate = mandate;
        this.locale = locale;
    }

    public static MandateCreatedEvent newEvent(Object source, User user, Mandate mandate, Locale locale) {
        if(mandate.getPillar() == 2) {
            return new SecondPillarMandateCreatedEvent(source, user, mandate, locale);
        } else if(mandate.getPillar() == 3) {
            return new ThirdPillarMandateCreatedEvent(source, user, mandate, locale);
        } else {
            throw new IllegalArgumentException("Event for the pillar type is not available");
        }
    }

    public Long getMandateId() {
        return mandate.getId();
    }

    public byte[] getSignedFile() {
        return mandate.getMandate()
            .orElseThrow(() -> new IllegalStateException("Expecting mandate to be signed after creation"));
    }
}
