package ee.tuleva.onboarding.auth.smartid;

import static ee.sk.smartid.FlowType.NOTIFICATION;
import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember;
import static ee.tuleva.onboarding.auth.GrantType.GRANT_TYPE;
import static ee.tuleva.onboarding.auth.GrantType.ID_CARD;
import static ee.tuleva.onboarding.auth.GrantType.MOBILE_ID;
import static ee.tuleva.onboarding.auth.GrantType.SMART_ID;
import static ee.tuleva.onboarding.auth.principal.AuthenticatedPerson.SMART_ID_DOCUMENT_NUMBER;
import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.aDeviceLinkSession;
import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.aNotificationSession;
import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.aSmartIdPerson;
import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.documentNumber;
import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.personalCode;
import static ee.tuleva.onboarding.error.response.ErrorsResponse.ofSingleError;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.principal.PrincipalService;
import ee.tuleva.onboarding.auth.response.AuthNotCompleteException;
import ee.tuleva.onboarding.auth.session.GenericSessionStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class SmartIdAuthProviderTest {

  private final Instant now = Instant.parse("2026-09-02T10:00:00Z");
  private final GenericSessionStore sessionStore = mock(GenericSessionStore.class);
  private final SmartIdAuthService smartIdAuthService = mock(SmartIdAuthService.class);
  private final RememberedSmartIdAccounts rememberedAccounts =
      mock(RememberedSmartIdAccounts.class);
  private final PrincipalService principalService = mock(PrincipalService.class);
  private final SmartIdAuthProvider provider =
      new SmartIdAuthProvider(
          sessionStore,
          smartIdAuthService,
          rememberedAccounts,
          principalService,
          Clock.fixed(now, ZoneOffset.UTC));

  @Test
  void supportsOnlySmartId() {
    assertThat(provider.supports(SMART_ID)).isTrue();
    assertThat(provider.supports(MOBILE_ID)).isFalse();
    assertThat(provider.supports(ID_CARD)).isFalse();
  }

  @Test
  void throwsWhenThereIsNoSmartIdSession() {
    given(sessionStore.get(SmartIdSession.class)).willReturn(Optional.empty());

    assertThatThrownBy(() -> provider.authenticate(null))
        .isInstanceOf(SmartIdSessionNotFoundException.class);
  }

  @Test
  void throwsWhenTheLoginIsOlderThanThreeMinutes() {
    SmartIdSession session = aDeviceLinkSession(now.minusSeconds(181));
    String secret = session.issueRedemptionSecret();
    given(sessionStore.get(SmartIdSession.class)).willReturn(Optional.of(session));

    assertThatThrownBy(() -> provider.authenticate(secret))
        .isInstanceOf(SmartIdSessionNotFoundException.class);
    verify(smartIdAuthService, never()).completeLogin(session);
  }

  @Test
  void throwsWhenTheLoginIsExactlyThreeMinutesOld() {
    SmartIdSession session = aDeviceLinkSession(now.minusSeconds(180));
    String secret = session.issueRedemptionSecret();
    given(sessionStore.get(SmartIdSession.class)).willReturn(Optional.of(session));

    assertThatThrownBy(() -> provider.authenticate(secret))
        .isInstanceOf(SmartIdSessionNotFoundException.class);
    verify(smartIdAuthService, never()).completeLogin(session);
  }

  @Test
  void leavesTheStoredSessionAloneWhileTheLoginIsNotComplete() {
    SmartIdSession session = aDeviceLinkSession(now.minusSeconds(170));
    String secret = session.issueRedemptionSecret();
    given(sessionStore.get(SmartIdSession.class)).willReturn(Optional.of(session));
    given(smartIdAuthService.completeLogin(session)).willThrow(new AuthNotCompleteException());

    assertThatThrownBy(() -> provider.authenticate(secret))
        .isInstanceOf(AuthNotCompleteException.class);

    // Polling and the same-device callback are separate requests holding separate copies, so a
    // poll that writes back would undo a callback that landed while it was in flight.
    verify(sessionStore, never()).save(session);
    verify(rememberedAccounts, never()).remember(aSmartIdPerson(), true);
  }

  @Test
  void grantsThePersonOnceWithTheDocumentNumberAndRemembersTheAccountWhenAskedTo() {
    SmartIdSession session = aDeviceLinkSession(now, true);
    AuthenticatedPerson expected = sampleAuthenticatedPersonAndMember().build();
    String secret = givenACompletedLogin(session, expected);

    AuthenticatedPerson person = provider.authenticate(secret);

    assertThat(person).isEqualTo(expected);
    verify(sessionStore).remove(SmartIdSession.class);
    verify(sessionStore, never()).save(session);
    verify(rememberedAccounts).remember(aSmartIdPerson(), true);
    verify(rememberedAccounts, never()).forgetOnThisBrowser(any());
  }

  @Test
  void grantsThePersonWithTheSmartIdFlowThatCompletedTheLogin() {
    SmartIdSession session = aNotificationSession(now);
    String secret = session.issueRedemptionSecret();
    AuthenticatedPerson expected = sampleAuthenticatedPersonAndMember().build();
    given(sessionStore.get(SmartIdSession.class)).willReturn(Optional.of(session));
    given(smartIdAuthService.completeLogin(session)).willReturn(aSmartIdPerson(NOTIFICATION));
    given(
            principalService.getFrom(
                aSmartIdPerson(NOTIFICATION),
                Map.of(
                    GRANT_TYPE,
                    SMART_ID.name(),
                    SMART_ID_DOCUMENT_NUMBER,
                    documentNumber,
                    "smartIdFlow",
                    "NOTIFICATION")))
        .willReturn(expected);

    AuthenticatedPerson person = provider.authenticate(secret);

    assertThat(person).isEqualTo(expected);
  }

  @Test
  void aDeviceLinkLoginNotAskedToBeRememberedForgetsThePersonOnThisBrowserAndRemembersNothing() {
    SmartIdSession session = aDeviceLinkSession(now, false);
    AuthenticatedPerson expected = sampleAuthenticatedPersonAndMember().build();
    String secret = givenACompletedLogin(session, expected);

    AuthenticatedPerson person = provider.authenticate(secret);

    assertThat(person).isEqualTo(expected);
    verify(rememberedAccounts).forgetOnThisBrowser(personalCode);
    verify(rememberedAccounts, never()).remember(any(), anyBoolean());
  }

  @Test
  void aPushLoginCarriesTheRememberedAccountForward() {
    SmartIdSession session = aNotificationSession(now);
    AuthenticatedPerson expected = sampleAuthenticatedPersonAndMember().build();
    String secret = givenACompletedLogin(session, expected);

    provider.authenticate(secret);

    verify(rememberedAccounts).remember(aSmartIdPerson(), false);
    verify(rememberedAccounts, never()).forgetOnThisBrowser(any());
  }

  private String givenACompletedLogin(SmartIdSession session, AuthenticatedPerson person) {
    String secret = session.issueRedemptionSecret();
    given(sessionStore.get(SmartIdSession.class)).willReturn(Optional.of(session));
    given(smartIdAuthService.completeLogin(session)).willReturn(aSmartIdPerson());
    given(
            principalService.getFrom(
                aSmartIdPerson(),
                Map.of(
                    GRANT_TYPE,
                    SMART_ID.name(),
                    SMART_ID_DOCUMENT_NUMBER,
                    documentNumber,
                    "smartIdFlow",
                    "QR")))
        .willReturn(person);
    return secret;
  }

  @Test
  void propagatesLoginErrorsAndSavesTheSession() {
    SmartIdSession session = aDeviceLinkSession(now);
    session.setError(SmartIdLoginError.USER_REFUSED);
    String secret = session.issueRedemptionSecret();
    given(sessionStore.get(SmartIdSession.class)).willReturn(Optional.of(session));
    given(smartIdAuthService.completeLogin(session))
        .willThrow(new SmartIdException(ofSingleError("smart.id.user.refused", "refused")));

    assertThatThrownBy(() -> provider.authenticate(secret)).isInstanceOf(SmartIdException.class);

    verify(sessionStore).save(session);
    verify(rememberedAccounts, never()).forgetEverywhere();
  }

  @Test
  void forgetsTheRememberedAccountWhenItsPushLoginFindsNoAccount() {
    SmartIdSession session = aNotificationSession(now);
    session.setError(SmartIdLoginError.ACCOUNT_NOT_FOUND);
    String secret = session.issueRedemptionSecret();
    given(sessionStore.get(SmartIdSession.class)).willReturn(Optional.of(session));
    given(smartIdAuthService.completeLogin(session))
        .willThrow(new SmartIdException(ofSingleError("smart.id.account.not.found", "gone")));

    assertThatThrownBy(() -> provider.authenticate(secret)).isInstanceOf(SmartIdException.class);

    verify(rememberedAccounts).forgetEverywhere();
  }

  @Test
  void keepsTheRememberedAccountWhenItsPushLoginFindsTheAccountUnusableForNow() {
    SmartIdSession session = aNotificationSession(now);
    session.setError(SmartIdLoginError.ACCOUNT_UNUSABLE);
    String secret = session.issueRedemptionSecret();
    given(sessionStore.get(SmartIdSession.class)).willReturn(Optional.of(session));
    given(smartIdAuthService.completeLogin(session))
        .willThrow(new SmartIdException(SmartIdLoginError.ACCOUNT_UNUSABLE));

    assertThatThrownBy(() -> provider.authenticate(secret)).isInstanceOf(SmartIdException.class);

    verify(rememberedAccounts, never()).forgetEverywhere();
  }

  @Test
  void keepsTheRememberedAccountWhenAQrLoginFindsNoAccount() {
    SmartIdSession session = aDeviceLinkSession(now);
    session.setError(SmartIdLoginError.ACCOUNT_NOT_FOUND);
    String secret = session.issueRedemptionSecret();
    given(sessionStore.get(SmartIdSession.class)).willReturn(Optional.of(session));
    given(smartIdAuthService.completeLogin(session))
        .willThrow(new SmartIdException(ofSingleError("smart.id.account.not.found", "gone")));

    assertThatThrownBy(() -> provider.authenticate(secret)).isInstanceOf(SmartIdException.class);

    verify(rememberedAccounts, never()).forgetEverywhere();
  }

  @Test
  void aCompletedPushLoginReleasesThisBrowserBeforeTheRememberedAccountRenewsItsCookie() {
    SmartIdSession session = aNotificationSession(now);
    session.setPushLoginClaimedAt(now);
    String secret = session.issueRedemptionSecret();
    given(sessionStore.get(SmartIdSession.class)).willReturn(Optional.of(session));
    given(smartIdAuthService.completeLogin(session)).willReturn(aSmartIdPerson());
    given(
            principalService.getFrom(
                aSmartIdPerson(),
                Map.of(
                    GRANT_TYPE,
                    SMART_ID.name(),
                    SMART_ID_DOCUMENT_NUMBER,
                    documentNumber,
                    "smartIdFlow",
                    "QR")))
        .willReturn(sampleAuthenticatedPersonAndMember().build());

    provider.authenticate(secret);

    InOrder inOrder = inOrder(rememberedAccounts);
    inOrder.verify(rememberedAccounts).releaseNotificationLoginStart(now);
    inOrder.verify(rememberedAccounts).remember(aSmartIdPerson(), false);
  }

  @Test
  void aFailedPushLoginReleasesThisBrowserForTheNextOne() {
    SmartIdSession session = aNotificationSession(now);
    session.setPushLoginClaimedAt(now);
    session.setError(SmartIdLoginError.USER_REFUSED);
    String secret = session.issueRedemptionSecret();
    given(sessionStore.get(SmartIdSession.class)).willReturn(Optional.of(session));
    given(smartIdAuthService.completeLogin(session))
        .willThrow(new SmartIdException(SmartIdLoginError.USER_REFUSED));

    assertThatThrownBy(() -> provider.authenticate(secret)).isInstanceOf(SmartIdException.class);

    verify(rememberedAccounts).releaseNotificationLoginStart(now);
  }

  @Test
  void aPushLoginStillWaitingForThePersonKeepsThisBrowserClaimed() {
    SmartIdSession session = aNotificationSession(now);
    session.setPushLoginClaimedAt(now);
    String secret = session.issueRedemptionSecret();
    given(sessionStore.get(SmartIdSession.class)).willReturn(Optional.of(session));
    given(smartIdAuthService.completeLogin(session)).willThrow(new AuthNotCompleteException());

    assertThatThrownBy(() -> provider.authenticate(secret))
        .isInstanceOf(AuthNotCompleteException.class);

    verify(rememberedAccounts, never()).releaseNotificationLoginStart(any());
  }

  @Test
  void aQrLoginLeavesThePushLoginClaimOfThisBrowserAlone() {
    SmartIdSession session = aDeviceLinkSession(now);
    String secret = session.issueRedemptionSecret();
    given(sessionStore.get(SmartIdSession.class)).willReturn(Optional.of(session));
    given(smartIdAuthService.completeLogin(session)).willReturn(aSmartIdPerson());

    provider.authenticate(secret);

    verify(rememberedAccounts, never()).releaseNotificationLoginStart(any());
  }

  @Test
  void refusesARedemptionWithoutTheSecretTheLoginStartIssuedBeforeAskingSmartId() {
    SmartIdSession session = aDeviceLinkSession(now);
    session.issueRedemptionSecret();
    given(sessionStore.get(SmartIdSession.class)).willReturn(Optional.of(session));

    assertThatThrownBy(() -> provider.authenticate(null))
        .isInstanceOf(SmartIdSessionNotFoundException.class);
    assertThatThrownBy(
            () ->
                provider.authenticate(
                    new SmartIdSession(now, session.getLogin(), false).issueRedemptionSecret()))
        .isInstanceOf(SmartIdSessionNotFoundException.class);
    verify(smartIdAuthService, never()).completeLogin(session);
    verify(sessionStore, never()).remove(SmartIdSession.class);
  }
}
