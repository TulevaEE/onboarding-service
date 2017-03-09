package ee.tuleva.onboarding.auth;

import ee.tuleva.onboarding.user.User;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.io.Serializable;
import java.util.Collection;

public class PersonalCodeAuthentication<C extends Serializable> extends AbstractAuthenticationToken {

    private static final long serialVersionUID = -5988919052905713277L;

    private final User principal;
    private final C credentials;

    public PersonalCodeAuthentication(User user, C credentials, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principal = user;
        this.credentials = credentials;
        setAuthenticated(false);
    }

    @Override
    public C getCredentials() {
        return this.credentials;
    }

    @Override
    public User getPrincipal() {
        return this.principal;
    }
}
