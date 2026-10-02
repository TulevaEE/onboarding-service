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
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MobileIdAuthProvider implements AuthProvider {
  private final GenericSessionStore genericSessionStore;
  private final MobileIdAuthService mobileIdAuthService;
  private final PrincipalService principalService;
  private final RememberedMobileIdPhones rememberedPhones;

  @Override
  public boolean supports(GrantType grantType) {
    return MOBILE_ID.equals(grantType);
  }

  @Override
  public AuthenticatedPerson authenticate(@Nullable String authenticationHash) {
    Optional<MobileIDSession> session = genericSessionStore.get(MobileIDSession.class);
    if (session.isEmpty()) {
      throw new MobileIdSessionNotFoundException();
    }
    MobileIDSession mobileIdSession = session.get();

    if (!isLoginComplete(mobileIdSession)) {
      throw new AuthNotCompleteException();
    }
    releaseThisBrowserForTheNextLogin(mobileIdSession);

    AuthenticatedPerson authenticatedPerson =
        principalService.getFrom(
            mobileIdSession,
            Map.of(PHONE_NUMBER, mobileIdSession.getPhoneNumber(), GRANT_TYPE, MOBILE_ID.name()));
    rememberOrForgetOnThisBrowser(authenticatedPerson.getPersonalCode(), mobileIdSession);
    return authenticatedPerson;
  }

  private void rememberOrForgetOnThisBrowser(String personalCode, MobileIDSession session) {
    if (session.isRememberMe()) {
      rememberedPhones.remember(personalCode, session.getPhoneNumber());
    } else {
      rememberedPhones.forgetOnThisBrowser(personalCode);
    }
  }

  private boolean isLoginComplete(MobileIDSession session) {
    try {
      return mobileIdAuthService.isLoginComplete(session);
    } catch (MobileIdNotMidClientException e) {
      releaseThisBrowserForTheNextLogin(session);
      Long rememberedPhoneId = session.getRememberedPhoneId();
      if (rememberedPhoneId == null) {
        throw e;
      }
      rememberedPhones.forget(rememberedPhoneId);
      throw MobileIdException.phoneNumberRequired();
    } catch (RuntimeException e) {
      releaseThisBrowserForTheNextLogin(session);
      throw e;
    }
  }

  private void releaseThisBrowserForTheNextLogin(MobileIDSession session) {
    if (session.getRememberedPhoneId() != null) {
      rememberedPhones.releaseLoginStart();
    }
  }
}
