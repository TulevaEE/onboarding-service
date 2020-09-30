package ee.tuleva.onboarding.auth.event;

import ee.tuleva.onboarding.auth.principal.Person;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;
import org.springframework.security.oauth2.common.OAuth2AccessToken;

@Getter
public class AfterTokenGrantedEvent extends ApplicationEvent {

    private final Person person;
    private final OAuth2AccessToken accessToken;

    public AfterTokenGrantedEvent(Object source, Person person, OAuth2AccessToken accessToken) {
        super(source);
        this.person = person;
        this.accessToken = accessToken;
    }
}
