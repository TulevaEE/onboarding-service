package ee.tuleva.onboarding.auth;

import org.springframework.context.ApplicationEvent;
import org.springframework.security.oauth2.provider.OAuth2Authentication;

public class BeforeTokenGrantedEvent extends ApplicationEvent {

    OAuth2Authentication authentication;

    public BeforeTokenGrantedEvent(Object source, OAuth2Authentication authentication) {
        super(source);
        this.authentication = authentication;
    }

    public OAuth2Authentication getAuthentication() {
        return this.authentication;
    }

}