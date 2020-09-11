package ee.tuleva.onboarding.user.event;

import ee.tuleva.onboarding.user.User;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class BeforeUserCreatedEvent {

    private User user;

    public BeforeUserCreatedEvent(User user) {
        this.user = user;
    }
}
