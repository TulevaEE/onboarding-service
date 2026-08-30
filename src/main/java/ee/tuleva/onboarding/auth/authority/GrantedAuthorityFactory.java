package ee.tuleva.onboarding.auth.authority;

import static ee.tuleva.onboarding.auth.authority.Authority.MEMBER;
import static ee.tuleva.onboarding.auth.authority.Authority.USER;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.principal.PrincipalUsers;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class GrantedAuthorityFactory {

  private final PrincipalUsers principalUsers;

  public List<? extends GrantedAuthority> from(AuthenticatedPerson authenticatedPerson) {
    Long userId = authenticatedPerson.getUserIdOrThrow();

    List<SimpleGrantedAuthority> grantedAuthorities =
        principalUsers.isMember(userId)
            ? List.of(new SimpleGrantedAuthority(USER), new SimpleGrantedAuthority(MEMBER))
            : List.of(new SimpleGrantedAuthority(USER));

    log.info("User #{} granted authorities: {}", userId, grantedAuthorities);

    return grantedAuthorities;
  }
}
