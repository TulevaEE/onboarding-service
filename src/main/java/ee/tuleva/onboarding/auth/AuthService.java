package ee.tuleva.onboarding.auth;

import ee.tuleva.onboarding.auth.authority.GrantedAuthorityFactory;
import ee.tuleva.onboarding.auth.event.AfterTokenGrantedEvent;
import ee.tuleva.onboarding.auth.event.BeforeTokenGrantedEvent;
import ee.tuleva.onboarding.auth.jwt.JwtTokenUtil;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {
  private final List<AuthProvider> providers;
  private final ApplicationEventPublisher eventPublisher;
  private final JwtTokenUtil jwtTokenUtil;
  private final GrantedAuthorityFactory grantedAuthorityFactory;

  public String authenticate(GrantType grantType, String authenticationHash) {

    final var authenticatedPerson =
        providers.stream()
            .filter(it -> it.supports(grantType))
            .map(it -> it.authenticate(authenticationHash))
            .findFirst()
            .orElse(null);

    if (authenticatedPerson != null) {
      final var authorities = grantedAuthorityFactory.from(authenticatedPerson);

      eventPublisher.publishEvent(
          new BeforeTokenGrantedEvent(this, authenticatedPerson, grantType));

      String jwtToken = jwtTokenUtil.generateToken(authenticatedPerson, authorities);

      eventPublisher.publishEvent(new AfterTokenGrantedEvent(this, authenticatedPerson, jwtToken));

      return jwtToken;
    }
    return null;
  }
}
