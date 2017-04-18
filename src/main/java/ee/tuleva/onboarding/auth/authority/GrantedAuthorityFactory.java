package ee.tuleva.onboarding.auth.authority;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

public class GrantedAuthorityFactory {

    public static Collection<GrantedAuthority> from(AuthenticatedPerson authenticatedPerson) {
        return authenticatedPerson.getUser().getMember()
                .map( member -> Arrays.asList(new SimpleGrantedAuthority(Authority.MEMBER)))
                .orElse(Collections.EMPTY_LIST);
    }

}
