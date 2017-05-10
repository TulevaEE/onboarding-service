package ee.tuleva.onboarding.auth.authority;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.List;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

@Service
@RequiredArgsConstructor
@Slf4j
public class GrantedAuthorityFactory {

    private final UserService userService;

    public List<? extends GrantedAuthority> from(AuthenticatedPerson authenticatedPerson) {
        Long userId = authenticatedPerson.getUserId();
        User user = userService.getById(userId);

        List<SimpleGrantedAuthority> grantedAuthorities = user.getMember()
          .map(member -> singletonList(new SimpleGrantedAuthority(Authority.MEMBER)))
          .orElse(emptyList());

        log.info("User #{} granted authorities: {}", userId, grantedAuthorities);

        return grantedAuthorities;
    }

}
