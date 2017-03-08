package ee.tuleva.onboarding.auth;

import ee.tuleva.onboarding.user.User;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

public class PersonalCodeAuthentication extends AbstractAuthenticationToken {

    private final Object principal;
    private final Object credentials;

    public PersonalCodeAuthentication(User user, Object credentials, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principal = user;
        this.credentials = credentials;
        setAuthenticated(false);
    }

    @Override
    public Object getCredentials() {
        return this.credentials;
    }

    @Override
    public Object getPrincipal() {
        return this.principal;
    }
}
