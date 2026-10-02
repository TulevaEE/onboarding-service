package ee.tuleva.onboarding.auth.mobileid;

import static ee.tuleva.onboarding.auth.browser.PushLogin.MOBILE_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.auth.browser.RememberedBrowser;
import ee.tuleva.onboarding.auth.browser.ThisBrowser;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RememberedMobileIdPhonesTest {

  private static final Instant NOW = Instant.parse("2026-10-01T10:00:00Z");
  private static final Duration TWELVE_MONTHS = Duration.ofDays(365);
  private static final long BROWSER_ID = 7L;

  private final ThisBrowser thisBrowser = mock(ThisBrowser.class);
  private final RememberedMobileIdPhoneRepository repository =
      mock(RememberedMobileIdPhoneRepository.class);
  private final RememberedMobileIdPhones phones =
      new RememberedMobileIdPhones(
          thisBrowser, repository, TWELVE_MONTHS, Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void aSuccessfulLoginRemembersThePersonsPhoneOnThisBrowserForTwelveMonthsFromNow() {
    given(thisBrowser.rememberUntil(NOW.plus(TWELVE_MONTHS))).willReturn(BROWSER_ID);

    phones.remember("38888888888", "+37255555555", "AADU");

    verify(repository)
        .save(BROWSER_ID, "38888888888", "+37255555555", "AADU", NOW.plus(TWELVE_MONTHS));
  }

  @Test
  void aPersonIsRememberedWhenThisBrowserHoldsTheirPhone() {
    given(thisBrowser.remembered())
        .willReturn(Optional.of(new RememberedBrowser(BROWSER_ID, NOW.plus(TWELVE_MONTHS))));
    given(repository.findUnexpired(BROWSER_ID, "38888888888"))
        .willReturn(Optional.of(new RememberedMobileIdPhone(3L, "+37255555555")));

    assertThat(phones.isRemembered("38888888888")).isTrue();
  }

  @Test
  void aPersonIsNotRememberedWhenThisBrowserHoldsNoPhoneForThem() {
    given(thisBrowser.remembered())
        .willReturn(Optional.of(new RememberedBrowser(BROWSER_ID, NOW.plus(TWELVE_MONTHS))));
    given(repository.findUnexpired(BROWSER_ID, "38888888888")).willReturn(Optional.empty());

    assertThat(phones.isRemembered("38888888888")).isFalse();
  }

  @Test
  void nobodyIsRememberedOnABrowserWithoutACookie() {
    given(thisBrowser.remembered()).willReturn(Optional.empty());

    assertThat(phones.isRemembered("38888888888")).isFalse();
  }

  @Test
  void claimsAMobileIdLoginStartForThisBrowser() {
    phones.claimLoginStart();

    verify(thisBrowser).claimLoginStart(MOBILE_ID);
  }

  @Test
  void releasesTheMobileIdLoginStartForThisBrowser() {
    phones.releaseLoginStart();

    verify(thisBrowser).releaseLoginStart(MOBILE_ID);
  }

  @Test
  void forgettingAPersonOnThisBrowserDropsThePhoneThisBrowserRemembersForThem() {
    given(thisBrowser.remembered())
        .willReturn(Optional.of(new RememberedBrowser(BROWSER_ID, NOW.plus(TWELVE_MONTHS))));
    given(repository.findUnexpired(BROWSER_ID, "38888888888"))
        .willReturn(Optional.of(new RememberedMobileIdPhone(3L, "+37255555555")));

    phones.forgetOnThisBrowser("38888888888");

    verify(repository).remove(3L);
    verify(thisBrowser, never()).rememberUntil(any());
  }

  @Test
  void forgettingAPersonOnABrowserWithoutACookieDoesNothing() {
    given(thisBrowser.remembered()).willReturn(Optional.empty());

    phones.forgetOnThisBrowser("38888888888");

    verify(repository, never()).remove(anyLong());
  }

  @Test
  void continuesAsThePersonThisBrowserRememberedLast() {
    var person =
        new RememberedMobileIdPerson(
            "38888888888", "AADU", new RememberedMobileIdPhone(3L, "+37255555555"));
    given(thisBrowser.remembered())
        .willReturn(Optional.of(new RememberedBrowser(BROWSER_ID, NOW.plus(TWELVE_MONTHS))));
    given(repository.findMostRecentUnexpired(BROWSER_ID)).willReturn(Optional.of(person));

    assertThat(phones.mostRecentPerson()).contains(person);
  }

  @Test
  void hasNobodyToContinueAsOnABrowserWithoutACookie() {
    given(thisBrowser.remembered()).willReturn(Optional.empty());

    assertThat(phones.mostRecentPerson()).isEmpty();
  }

  @Test
  void notYouForgetsThePersonThisBrowserRememberedLast() {
    given(thisBrowser.remembered())
        .willReturn(Optional.of(new RememberedBrowser(BROWSER_ID, NOW.plus(TWELVE_MONTHS))));
    given(repository.findMostRecentUnexpired(BROWSER_ID))
        .willReturn(
            Optional.of(
                new RememberedMobileIdPerson(
                    "38888888888", "AADU", new RememberedMobileIdPhone(3L, "+37255555555"))));

    phones.forgetMostRecentPerson();

    verify(repository).remove(3L);
  }

  @Test
  void forgetsOneRememberedPhone() {
    phones.forget(3L);

    verify(repository).remove(3L);
  }
}
