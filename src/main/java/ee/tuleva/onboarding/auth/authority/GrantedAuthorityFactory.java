package ee.tuleva.onboarding.auth.authority;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
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

  private final UserService userService;

  public List<? extends GrantedAuthority> from(AuthenticatedPerson authenticatedPerson) {
    Long userId = authenticatedPerson.getUserId();
    User user = userService.getById(userId);

    List<SimpleGrantedAuthority> grantedAuthorities =
        user.getMember()
            .map(
                member ->
                    asList(
                        new SimpleGrantedAuthority(Authority.USER),
                        new SimpleGrantedAuthority(Authority.MEMBER)))
            .orElse(singletonList(new SimpleGrantedAuthority(Authority.USER)));

    log.info("User #{} granted authorities: {}", userId, grantedAuthorities);

    return grantedAuthorities;
  }
}
