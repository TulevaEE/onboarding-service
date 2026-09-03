package ee.tuleva.onboarding.auth.smartid;

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
import static ee.tuleva.onboarding.error.response.ErrorsResponse.ofSingleError;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
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
    given(sessionStore.get(SmartIdSession.class)).willReturn(Optional.of(session));

    assertThatThrownBy(() -> provider.authenticate(null))
        .isInstanceOf(SmartIdSessionNotFoundException.class);
    verify(smartIdAuthService, never()).completeLogin(session);
  }

  @Test
  void keepsPollingAndSavesTheSessionWhileTheLoginIsNotComplete() {
    SmartIdSession session = aDeviceLinkSession(now.minusSeconds(170));
    given(sessionStore.get(SmartIdSession.class)).willReturn(Optional.of(session));
    given(smartIdAuthService.completeLogin(session)).willThrow(new AuthNotCompleteException());

    assertThatThrownBy(() -> provider.authenticate(null))
        .isInstanceOf(AuthNotCompleteException.class);

    verify(sessionStore).save(session);
    verify(rememberedAccounts, never()).remember(aSmartIdPerson(), true);
  }

  @Test
  void grantsThePersonWithTheDocumentNumberAndRemembersTheAccount() {
    SmartIdSession session = aDeviceLinkSession(now);
    AuthenticatedPerson expected = sampleAuthenticatedPersonAndMember().build();
    given(sessionStore.get(SmartIdSession.class)).willReturn(Optional.of(session));
    given(smartIdAuthService.completeLogin(session)).willReturn(aSmartIdPerson());
    given(
            principalService.getFrom(
                aSmartIdPerson(),
                Map.of(GRANT_TYPE, SMART_ID.name(), SMART_ID_DOCUMENT_NUMBER, documentNumber)))
        .willReturn(expected);

    AuthenticatedPerson person = provider.authenticate(null);

    assertThat(person).isEqualTo(expected);
    verify(sessionStore).save(session);
    verify(rememberedAccounts).remember(aSmartIdPerson(), true);
  }

  @Test
  void propagatesLoginErrorsAndSavesTheSession() {
    SmartIdSession session = aDeviceLinkSession(now);
    session.setError(SmartIdLoginError.USER_REFUSED);
    given(sessionStore.get(SmartIdSession.class)).willReturn(Optional.of(session));
    given(smartIdAuthService.completeLogin(session))
        .willThrow(new SmartIdException(ofSingleError("smart.id.user.refused", "refused")));

    assertThatThrownBy(() -> provider.authenticate(null)).isInstanceOf(SmartIdException.class);

    verify(sessionStore).save(session);
    verify(rememberedAccounts, never()).forgetEverywhere();
  }

  @Test
  void forgetsTheRememberedAccountWhenItsPushLoginFindsNoAccount() {
    SmartIdSession session = aNotificationSession(now);
    session.setError(SmartIdLoginError.ACCOUNT_NOT_FOUND);
    given(sessionStore.get(SmartIdSession.class)).willReturn(Optional.of(session));
    given(smartIdAuthService.completeLogin(session))
        .willThrow(new SmartIdException(ofSingleError("smart.id.account.not.found", "gone")));

    assertThatThrownBy(() -> provider.authenticate(null)).isInstanceOf(SmartIdException.class);

    verify(rememberedAccounts).forgetEverywhere();
  }

  @Test
  void keepsTheRememberedAccountWhenAQrLoginFindsNoAccount() {
    SmartIdSession session = aDeviceLinkSession(now);
    session.setError(SmartIdLoginError.ACCOUNT_NOT_FOUND);
    given(sessionStore.get(SmartIdSession.class)).willReturn(Optional.of(session));
    given(smartIdAuthService.completeLogin(session))
        .willThrow(new SmartIdException(ofSingleError("smart.id.account.not.found", "gone")));

    assertThatThrownBy(() -> provider.authenticate(null)).isInstanceOf(SmartIdException.class);

    verify(rememberedAccounts, never()).forgetEverywhere();
  }
}
