package ee.tuleva.onboarding.auth.authority;

import static ee.tuleva.onboarding.auth.authority.Authority.MEMBER;
import static ee.tuleva.onboarding.auth.authority.Authority.USER;
import static ee.tuleva.onboarding.auth.authority.Authority.WARD;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.principal.PrincipalUsers;
import ee.tuleva.onboarding.auth.role.ChildRepresentations;
import java.util.ArrayList;
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
  private final ChildRepresentations childRepresentations;

  public List<? extends GrantedAuthority> from(AuthenticatedPerson authenticatedPerson) {
    Long userId = authenticatedPerson.getUserIdOrThrow();

    var grantedAuthorities = new ArrayList<SimpleGrantedAuthority>();
    grantedAuthorities.add(new SimpleGrantedAuthority(USER));
    if (isWard(authenticatedPerson)) {
      grantedAuthorities.add(new SimpleGrantedAuthority(WARD));
    }
    if (principalUsers.isMember(userId)) {
      grantedAuthorities.add(new SimpleGrantedAuthority(MEMBER));
    }

    log.info("User #{} granted authorities: {}", userId, grantedAuthorities);

    return List.copyOf(grantedAuthorities);
  }

  private boolean isWard(AuthenticatedPerson person) {
    return person.isActingAsSelf()
        && childRepresentations.hasRestrictedLegalCapacity(person.getPersonalCode());
  }
}
