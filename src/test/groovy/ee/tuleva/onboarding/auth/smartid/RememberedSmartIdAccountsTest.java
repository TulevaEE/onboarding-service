package ee.tuleva.onboarding.auth.smartid;

import static ee.tuleva.onboarding.auth.browser.PushLogin.SMART_ID_NOTIFICATION;
import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.aRememberedAccount;
import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.aSmartIdPerson;
import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.documentNumber;
import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.firstName;
import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.lastName;
import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.personalCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.auth.browser.PushLoginStartedTooSoonException;
import ee.tuleva.onboarding.auth.browser.RememberedBrowser;
import ee.tuleva.onboarding.auth.browser.ThisBrowser;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RememberedSmartIdAccountsTest {

  private static final Instant NOW = Instant.parse("2026-09-03T10:00:00Z");
  private static final Duration VALIDITY = Duration.ofDays(90);
  private static final long BROWSER_ID = 7L;

  private final ThisBrowser thisBrowser = mock(ThisBrowser.class);
  private final RememberedSmartIdAccountRepository repository =
      mock(RememberedSmartIdAccountRepository.class);
  private final RememberedSmartIdAccounts accounts =
      new RememberedSmartIdAccounts(
          thisBrowser, repository, VALIDITY, Clock.fixed(NOW, ZoneOffset.UTC));

  private static VerifiedSmartIdAccount verifiedAt(Instant verifiedAt) {
    return new VerifiedSmartIdAccount(
        personalCode, documentNumber, firstName, lastName, verifiedAt);
  }

  private void browserRemembering(VerifiedSmartIdAccount account) {
    given(thisBrowser.remembered())
        .willReturn(Optional.of(new RememberedBrowser(BROWSER_ID, NOW.plus(VALIDITY))));
    given(repository.findUnexpired(BROWSER_ID)).willReturn(Optional.of(account));
  }

  @Test
  void aDeviceLinkLoginRemembersTheAccountOnThisBrowserForTheValidity() {
    given(thisBrowser.rememberUntil(NOW.plus(VALIDITY))).willReturn(BROWSER_ID);

    accounts.remember(aSmartIdPerson(), true);

    verify(repository).replace(BROWSER_ID, verifiedAt(NOW), NOW.plus(VALIDITY));
  }

  @Test
  void readsBackTheAccountThisBrowserRemembers() {
    browserRemembering(verifiedAt(NOW));

    assertThat(accounts.current()).contains(aRememberedAccount());
  }

  @Test
  void hasNoAccountOnABrowserThatIsNotRemembered() {
    given(thisBrowser.remembered()).willReturn(Optional.empty());

    assertThat(accounts.current()).isEmpty();
  }

  @Test
  void hasNoAccountWhenTheBrowserRemembersNoSmartIdAccount() {
    given(thisBrowser.remembered())
        .willReturn(Optional.of(new RememberedBrowser(BROWSER_ID, NOW.plus(VALIDITY))));
    given(repository.findUnexpired(BROWSER_ID)).willReturn(Optional.empty());

    assertThat(accounts.current()).isEmpty();
  }

  @Test
  void aPushLoginCarriesTheEarlierVerificationForwardRatherThanExtendingIt() {
    Instant verifiedAt = NOW.minus(Duration.ofDays(30));
    browserRemembering(verifiedAt(verifiedAt));
    given(thisBrowser.rememberUntil(verifiedAt.plus(VALIDITY))).willReturn(BROWSER_ID);

    accounts.remember(aSmartIdPerson(), false);

    verify(repository).replace(BROWSER_ID, verifiedAt(verifiedAt), verifiedAt.plus(VALIDITY));
  }

  @Test
  void aDeviceLinkLoginStartsTheValidityAgain() {
    browserRemembering(verifiedAt(NOW.minus(Duration.ofDays(30))));
    given(thisBrowser.rememberUntil(NOW.plus(VALIDITY))).willReturn(BROWSER_ID);

    accounts.remember(aSmartIdPerson(), true);

    verify(repository).replace(BROWSER_ID, verifiedAt(NOW), NOW.plus(VALIDITY));
  }

  @Test
  void aPushLoginWithNothingToCarryForwardRemembersNothing() {
    given(thisBrowser.remembered()).willReturn(Optional.empty());

    accounts.remember(aSmartIdPerson(), false);

    verify(thisBrowser, never()).rememberUntil(any());
    verify(repository, never()).replace(anyLong(), any(), any());
  }

  @Test
  void forgettingDropsTheAccountThisBrowserRemembersAndNothingElse() {
    given(thisBrowser.remembered())
        .willReturn(Optional.of(new RememberedBrowser(BROWSER_ID, NOW.plus(VALIDITY))));

    accounts.forget();

    verify(repository).remove(BROWSER_ID);
    verify(repository, never()).removeAllOf(any());
  }

  @Test
  void forgettingOnABrowserThatIsNotRememberedDoesNothing() {
    given(thisBrowser.remembered()).willReturn(Optional.empty());

    accounts.forget();

    verify(repository, never()).remove(anyLong());
  }

  @Test
  void forgettingEverywhereDropsThePersonsSmartIdAccountOnEveryBrowser() {
    browserRemembering(verifiedAt(NOW));

    accounts.forgetEverywhere();

    verify(repository).removeAllOf(personalCode);
    verify(repository, never()).remove(anyLong());
  }

  @Test
  void forgettingEverywhereDoesNothingWhenThisBrowserRemembersNoAccount() {
    given(thisBrowser.remembered()).willReturn(Optional.empty());

    accounts.forgetEverywhere();

    verify(repository, never()).removeAllOf(any());
  }

  @Test
  void claimsAPushLoginStartForThisBrowser() {
    accounts.claimNotificationLoginStart();

    verify(thisBrowser).claimLoginStart(SMART_ID_NOTIFICATION);
  }

  @Test
  void refusesAPushLoginStartedTooSoonAfterThePreviousOne() {
    willThrow(new PushLoginStartedTooSoonException(SMART_ID_NOTIFICATION))
        .given(thisBrowser)
        .claimLoginStart(SMART_ID_NOTIFICATION);

    assertThatThrownBy(accounts::claimNotificationLoginStart)
        .isInstanceOf(PushLoginStartedTooSoonException.class);
  }
}
