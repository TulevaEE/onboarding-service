package ee.tuleva.onboarding.mandate.event;

import ee.tuleva.onboarding.epis.contact.UserPreferences;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.user.User;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class BeforeMandateCreatedEvent extends ApplicationEvent {

    private final User user;
    private final Mandate mandate;
    private final UserPreferences contactDetails;

    public BeforeMandateCreatedEvent(Object source, User user, Mandate mandate, UserPreferences contactDetails) {
        super(source);
        this.user = user;
        this.mandate = mandate;
        this.contactDetails = contactDetails;
    }

    public Integer getPillar() {
        return mandate.getPillar();
    }
}
