package ee.tuleva.onboarding.auth.partner;

import static ee.tuleva.onboarding.auth.GrantType.PARTNER;

import ee.tuleva.onboarding.auth.AuthProvider;
import ee.tuleva.onboarding.auth.GrantType;
import ee.tuleva.onboarding.auth.jwt.JwtTokenUtil;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.auth.principal.PrincipalService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Profile("!production")
public class PartnerAuthProvider implements AuthProvider {

  private final JwtTokenUtil jwtTokenUtil;
  private final PrincipalService principalService;

  @Override
  public boolean supports(GrantType grantType) {
    return grantType == PARTNER;
  }

  @Override
  // TODO: use separate partner trust store
  public AuthenticatedPerson authenticate(String handoverToken) {
    Person person = jwtTokenUtil.getPersonFromToken(handoverToken);
    return principalService.getFrom(person, Map.of());
  }
}
