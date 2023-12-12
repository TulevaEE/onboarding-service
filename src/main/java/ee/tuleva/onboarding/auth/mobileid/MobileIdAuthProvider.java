package ee.tuleva.onboarding.auth.mobileid;

import static ee.tuleva.onboarding.auth.GrantType.GRANT_TYPE;
import static ee.tuleva.onboarding.auth.GrantType.MOBILE_ID;
import static ee.tuleva.onboarding.auth.mobileid.MobileIDSession.PHONE_NUMBER;

import ee.tuleva.onboarding.auth.AuthProvider;
import ee.tuleva.onboarding.auth.GrantType;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.principal.PrincipalService;
import ee.tuleva.onboarding.auth.response.AuthNotCompleteException;
import ee.tuleva.onboarding.auth.session.GenericSessionStore;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MobileIdAuthProvider implements AuthProvider {
  private final GenericSessionStore genericSessionStore;
  private final MobileIdAuthService mobileIdAuthService;
  private final PrincipalService principalService;

  @Override
  public boolean supports(GrantType grantType) {
    return MOBILE_ID.equals(grantType);
  }

  @Override
  public AuthenticatedPerson authenticate(String authenticationHash) {
    Optional<MobileIDSession> session = genericSessionStore.get(MobileIDSession.class);
    if (session.isEmpty()) {
      throw new MobileIdSessionNotFoundException();
    }
    MobileIDSession mobileIdSession = session.get();

    boolean isComplete = mobileIdAuthService.isLoginComplete(mobileIdSession);
    if (!isComplete) {
      throw new AuthNotCompleteException();
    }

    return principalService.getFrom(
        mobileIdSession,
        Map.of(PHONE_NUMBER, mobileIdSession.getPhoneNumber(), GRANT_TYPE, MOBILE_ID.name()));
  }
}
