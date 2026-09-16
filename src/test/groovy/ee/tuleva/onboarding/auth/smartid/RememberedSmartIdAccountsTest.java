package ee.tuleva.onboarding.auth.smartid;

import static ee.tuleva.onboarding.auth.smartid.RememberedSmartIdAccounts.COOKIE_NAME;
import static ee.tuleva.onboarding.auth.smartid.RememberedSmartIdAccounts.hash;
import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.aRememberedAccount;
import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.aSmartIdPerson;
import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.documentNumber;
import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.firstName;
import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.lastName;
import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.personalCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.http.HttpHeaders.SET_COOKIE;

import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class RememberedSmartIdAccountsTest {

  private static final Instant NOW = Instant.parse("2026-09-03T10:00:00Z");
  private static final Duration VALIDITY = Duration.ofDays(90);

  private final RememberedBrowsers browsers = mock(RememberedBrowsers.class);
  private final RememberedSmartIdAccounts accounts =
      new RememberedSmartIdAccounts(
          browsers, VALIDITY, "tuleva.ee", Clock.fixed(NOW, ZoneOffset.UTC));

  private final MockHttpServletRequest request = new MockHttpServletRequest();
  private final MockHttpServletResponse response = new MockHttpServletResponse();

  @AfterEach
  void clearRequestContext() {
    RequestContextHolder.resetRequestAttributes();
  }

  private void bindRequest(Cookie... cookies) {
    request.setCookies(cookies);
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, response));
  }

  private String cookieValue() {
    String header = Objects.requireNonNull(response.getHeader(SET_COOKIE));
    return header.substring(COOKIE_NAME.length() + 1, header.indexOf(';'));
  }

  private static RememberedBrowser browserVerifiedAt(Instant verifiedAt) {
    return new RememberedBrowser(personalCode, documentNumber, firstName, lastName, verifiedAt);
  }

  @Test
  void remembersABrowserBehindAnOpaqueTokenCarryingNoPersonalData() {
    bindRequest();

    accounts.remember(aSmartIdPerson(), true);

    String token = cookieValue();
    assertThat(token)
        .isNotBlank()
        .doesNotContain(personalCode, documentNumber, firstName, lastName);
    verify(browsers).add(hash(token), browserVerifiedAt(NOW), NOW.plus(VALIDITY));
  }

  @Test
  void setsAHardenedCookieOnTheSessionDomain() {
    bindRequest();

    accounts.remember(aSmartIdPerson(), true);

    assertThat(response.getHeader(SET_COOKIE))
        .startsWith(COOKIE_NAME + "=")
        .contains("Max-Age=" + VALIDITY.toSeconds())
        .contains("Domain=tuleva.ee")
        .contains("Path=/")
        .contains("Secure")
        .contains("HttpOnly")
        .contains("SameSite=Lax");
  }

  @Test
  void setsAValidCookieWhereNoCookieDomainIsConfigured() {
    var withoutDomain =
        new RememberedSmartIdAccounts(browsers, VALIDITY, null, Clock.fixed(NOW, ZoneOffset.UTC));
    bindRequest();

    withoutDomain.remember(aSmartIdPerson(), true);

    assertThat(response.getHeader(SET_COOKIE))
        .startsWith(COOKIE_NAME + "=")
        .doesNotContain("Domain")
        .contains("Secure")
        .contains("HttpOnly");
  }

  @Test
  void readsBackTheAccountTheTokenStandsFor() {
    bindRequest(new Cookie(COOKIE_NAME, "a-token"));
    given(browsers.findUnexpired(hash("a-token"))).willReturn(Optional.of(browserVerifiedAt(NOW)));

    assertThat(accounts.current()).contains(aRememberedAccount());
  }

  @Test
  void hasNoAccountWithoutACookie() {
    bindRequest();

    assertThat(accounts.current()).isEmpty();
  }

  @Test
  void hasNoAccountWhenTheTokenIsUnknownOrPastItsValidity() {
    bindRequest(new Cookie(COOKIE_NAME, "a-token"));
    given(browsers.findUnexpired(hash("a-token"))).willReturn(Optional.empty());

    assertThat(accounts.current()).isEmpty();
  }

  @Test
  void issuesAFreshTokenOnEveryLoginAndForgetsThePreviousOne() {
    bindRequest(new Cookie(COOKIE_NAME, "old-token"));
    given(browsers.findUnexpired(hash("old-token")))
        .willReturn(Optional.of(browserVerifiedAt(NOW)));

    accounts.remember(aSmartIdPerson(), true);

    verify(browsers).remove(hash("old-token"));
    assertThat(cookieValue()).isNotEqualTo("old-token");
  }

  @Test
  void aPushLoginCarriesTheEarlierVerificationForwardRatherThanExtendingIt() {
    Instant verifiedAt = NOW.minus(Duration.ofDays(30));
    bindRequest(new Cookie(COOKIE_NAME, "old-token"));
    given(browsers.findUnexpired(hash("old-token")))
        .willReturn(Optional.of(browserVerifiedAt(verifiedAt)));

    accounts.remember(aSmartIdPerson(), false);

    verify(browsers)
        .add(hash(cookieValue()), browserVerifiedAt(verifiedAt), verifiedAt.plus(VALIDITY));
    assertThat(response.getHeader(SET_COOKIE))
        .contains("Max-Age=" + Duration.ofDays(60).toSeconds());
  }

  @Test
  void aDeviceLinkLoginStartsTheValidityAgain() {
    bindRequest(new Cookie(COOKIE_NAME, "old-token"));
    given(browsers.findUnexpired(hash("old-token")))
        .willReturn(Optional.of(browserVerifiedAt(NOW.minus(Duration.ofDays(30)))));

    accounts.remember(aSmartIdPerson(), true);

    verify(browsers).add(hash(cookieValue()), browserVerifiedAt(NOW), NOW.plus(VALIDITY));
  }

  @Test
  void aPushLoginWithNothingToCarryForwardRemembersNothing() {
    bindRequest(new Cookie(COOKIE_NAME, "old-token"));
    given(browsers.findUnexpired(hash("old-token"))).willReturn(Optional.empty());

    accounts.remember(aSmartIdPerson(), false);

    verify(browsers, never()).add(any(), any(), any());
    assertThat(response.getHeader(SET_COOKIE)).isNull();
  }

  @Test
  void forgettingDropsThisBrowserOnlyAndExpiresTheCookie() {
    bindRequest(new Cookie(COOKIE_NAME, "a-token"));

    accounts.forget();

    verify(browsers).remove(hash("a-token"));
    verify(browsers, never()).removeAllOf(personalCode);
    assertThat(response.getHeader(SET_COOKIE)).contains("Max-Age=0");
  }

  @Test
  void forgettingEverywhereDropsEveryBrowserOfThatPerson() {
    bindRequest(new Cookie(COOKIE_NAME, "a-token"));
    given(browsers.findUnexpired(hash("a-token"))).willReturn(Optional.of(browserVerifiedAt(NOW)));

    accounts.forgetEverywhere();

    verify(browsers).removeAllOf(personalCode);
    assertThat(response.getHeader(SET_COOKIE)).contains("Max-Age=0");
  }

  @Test
  void forgettingEverywhereStillExpiresTheCookieWhenTheTokenIsAlreadyGone() {
    bindRequest(new Cookie(COOKIE_NAME, "a-token"));
    given(browsers.findUnexpired(hash("a-token"))).willReturn(Optional.empty());

    accounts.forgetEverywhere();

    verify(browsers, never()).removeAllOf(personalCode);
    assertThat(response.getHeader(SET_COOKIE)).contains("Max-Age=0");
  }
}
