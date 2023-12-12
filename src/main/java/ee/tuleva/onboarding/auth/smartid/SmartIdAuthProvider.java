package ee.tuleva.onboarding.auth.smartid;

import static ee.tuleva.onboarding.auth.GrantType.GRANT_TYPE;
import static ee.tuleva.onboarding.auth.GrantType.SMART_ID;

import ee.tuleva.onboarding.auth.AuthProvider;
import ee.tuleva.onboarding.auth.GrantType;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.principal.PrincipalService;
import ee.tuleva.onboarding.auth.response.AuthNotCompleteException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SmartIdAuthProvider implements AuthProvider {
  private final SmartIdAuthService smartIdAuthService;
  private final PrincipalService principalService;

  @Override
  public boolean supports(GrantType grantType) {
    return SMART_ID.equals(grantType);
  }

  @Override
  public AuthenticatedPerson authenticate(String authenticationHash) {
    if (authenticationHash == null) {
      throw new SmartIdSessionNotFoundException();
    }

    var identity = smartIdAuthService.getAuthenticationIdentity(authenticationHash);
    if (identity.isEmpty()) {
      throw new AuthNotCompleteException();
    }
    var smartIdPerson = new SmartIdPerson(identity.get());
    return principalService.getFrom(smartIdPerson, Map.of(GRANT_TYPE, SMART_ID.name()));
  }
}
