package ee.tuleva.onboarding.auth;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;
import org.springframework.security.oauth2.provider.OAuth2Authentication;

@Getter
public class BeforeTokenGrantedEvent extends ApplicationEvent {

    private final OAuth2Authentication authentication;
    private final GrantType grantType;

    public BeforeTokenGrantedEvent(Object source, OAuth2Authentication authentication, GrantType grantType) {
        super(source);
        this.authentication = authentication;
        this.grantType = grantType;
    }
}