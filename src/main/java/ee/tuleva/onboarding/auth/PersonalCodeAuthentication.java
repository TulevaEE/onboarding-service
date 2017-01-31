package ee.tuleva.onboarding.auth;

import com.codeborne.security.mobileid.MobileIDSession;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import ee.tuleva.onboarding.user.User;
import java.util.Collection;

public class PersonalCodeAuthentication extends AbstractAuthenticationToken {

    private final Object principal;
    private final MobileIDSession credentials;

    public PersonalCodeAuthentication(User user, MobileIDSession credentials, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principal = user;
        this.credentials = credentials;
        setAuthenticated(false);
    }

    @Override
    public MobileIDSession getCredentials() {
        return this.credentials;
    }

    @Override
    public Object getPrincipal() {
        return this.principal;
    }
}
