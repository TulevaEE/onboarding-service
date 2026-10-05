package ee.tuleva.onboarding.auth.smartid;

import static ee.tuleva.onboarding.auth.GrantType.GRANT_TYPE;
import static ee.tuleva.onboarding.auth.GrantType.SMART_ID;
import static ee.tuleva.onboarding.auth.principal.AuthenticatedPerson.SMART_ID_DOCUMENT_NUMBER;

import ee.tuleva.onboarding.auth.AuthProvider;
import ee.tuleva.onboarding.auth.GrantType;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.principal.PrincipalService;
import ee.tuleva.onboarding.auth.session.GenericSessionStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SmartIdAuthProvider implements AuthProvider {

  private static final Duration LOGIN_TTL = Duration.ofSeconds(180);

  private final GenericSessionStore genericSessionStore;
  private final SmartIdAuthService smartIdAuthService;
  private final RememberedSmartIdAccounts rememberedSmartIdAccounts;
  private final PrincipalService principalService;
  private final Clock clock;

  @Override
  public boolean supports(GrantType grantType) {
    return SMART_ID.equals(grantType);
  }

  @Override
  public AuthenticatedPerson authenticate(@Nullable String authenticationHash) {
    var session =
        genericSessionStore
            .get(SmartIdSession.class)
            .orElseThrow(SmartIdSessionNotFoundException::new);

    if (!session.isRedeemableWith(authenticationHash)) {
      throw new SmartIdSessionNotFoundException();
    }

    if (!Instant.now(clock).isBefore(session.getCreatedAt().plus(LOGIN_TTL))) {
      throw new SmartIdSessionNotFoundException();
    }

    SmartIdPerson person;
    try {
      person = smartIdAuthService.completeLogin(session);
    } catch (SmartIdException e) {
      genericSessionStore.save(session);
      releaseThisBrowserForTheNextPushLogin(session);
      forgetEveryBrowserRememberingAnAccountSmartIdNoLongerHas(session);
      throw e;
    }
    releaseThisBrowserForTheNextPushLogin(session);
    genericSessionStore.remove(SmartIdSession.class);

    var authenticatedPerson =
        principalService.getFrom(
            person,
            Map.of(
                GRANT_TYPE, SMART_ID.name(), SMART_ID_DOCUMENT_NUMBER, person.getDocumentNumber()));
    rememberOrForgetOnThisBrowser(person, session);
    return authenticatedPerson;
  }

  private void releaseThisBrowserForTheNextPushLogin(SmartIdSession session) {
    Instant claimedAt = session.getPushLoginClaimedAt();
    if (claimedAt != null) {
      rememberedSmartIdAccounts.releaseNotificationLoginStart(claimedAt);
    }
  }

  private void rememberOrForgetOnThisBrowser(SmartIdPerson person, SmartIdSession session) {
    if (session.isRememberMe()) {
      rememberedSmartIdAccounts.remember(person, session.getLogin() instanceof DeviceLinkLogin);
    } else {
      rememberedSmartIdAccounts.forgetOnThisBrowser(person.getPersonalCode());
    }
  }

  private void forgetEveryBrowserRememberingAnAccountSmartIdNoLongerHas(SmartIdSession session) {
    if (session.getError() == SmartIdLoginError.ACCOUNT_NOT_FOUND
        && session.getLogin() instanceof NotificationLogin) {
      rememberedSmartIdAccounts.forgetEverywhere();
    }
  }
}
