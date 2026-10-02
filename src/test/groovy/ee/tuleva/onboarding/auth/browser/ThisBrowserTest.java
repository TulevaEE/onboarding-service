package ee.tuleva.onboarding.auth.browser;

import static ee.tuleva.onboarding.auth.browser.PushLogin.SMART_ID_NOTIFICATION;
import static ee.tuleva.onboarding.auth.browser.ThisBrowser.COOKIE_NAME;
import static ee.tuleva.onboarding.auth.browser.ThisBrowser.hash;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.http.HttpHeaders.SET_COOKIE;

import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class ThisBrowserTest {

  private static final Instant NOW = Instant.parse("2026-09-03T10:00:00Z");
  private static final Duration TEN_SECONDS = Duration.ofSeconds(10);

  private final RememberedBrowsers browsers = mock(RememberedBrowsers.class);
  private final ThisBrowser thisBrowser = new ThisBrowser(browsers);

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

  @Test
  void remembersANewBrowserBehindAnOpaqueTokenOfWhichOnlyTheHashIsStored() {
    bindRequest();
    Instant until = NOW.plus(Duration.ofDays(90));
    given(browsers.add(any(), any())).willReturn(7L);

    long id = thisBrowser.rememberUntil(until);

    String token = cookieValue();
    assertThat(id).isEqualTo(7L);
    assertThat(token).hasSizeGreaterThanOrEqualTo(43);
    verify(browsers).add(hash(token), until);
  }

  @Test
  void setsAHardenedCookieOnlyTheHostThatSetItReceives() {
    bindRequest();
    given(browsers.add(any(), any())).willReturn(7L);

    thisBrowser.rememberUntil(NOW.plus(Duration.ofDays(90)));

    assertThat(response.getHeader(SET_COOKIE))
        .startsWith("__Host-")
        .startsWith(COOKIE_NAME + "=")
        .contains("Max-Age=" + Duration.ofDays(400).toSeconds())
        .doesNotContain("Domain")
        .contains("Path=/")
        .contains("Secure")
        .contains("HttpOnly")
        .contains("SameSite=Lax");
  }

  @Test
  void findsTheBrowserItsTokenStandsFor() {
    bindRequest(new Cookie(COOKIE_NAME, "a-token"));
    var browser = new RememberedBrowser(7L, NOW.plus(Duration.ofDays(10)));
    given(browsers.findUnexpired(hash("a-token"))).willReturn(Optional.of(browser));

    assertThat(thisBrowser.remembered()).contains(browser);
  }

  @Test
  void isNotRememberedWithoutACookie() {
    bindRequest();

    assertThat(thisBrowser.remembered()).isEmpty();
  }

  @Test
  void issuesAFreshTokenOnEveryLoginForTheSameBrowser() {
    bindRequest(new Cookie(COOKIE_NAME, "old-token"));
    Instant until = NOW.plus(Duration.ofDays(90));
    given(browsers.findUnexpired(hash("old-token")))
        .willReturn(Optional.of(new RememberedBrowser(7L, NOW.plus(Duration.ofDays(10)))));
    given(browsers.rotate(eq(7L), eq(hash("old-token")), any())).willReturn(true);

    long id = thisBrowser.rememberUntil(until);

    assertThat(id).isEqualTo(7L);
    assertThat(cookieValue()).isNotEqualTo("old-token");
    verify(browsers).rotate(7L, hash("old-token"), hash(cookieValue()));
    verify(browsers).extendUntil(7L, until);
    verify(browsers, never()).add(any(), any());
  }

  @Test
  void theCookieOutlivesWhateverTheBrowserRemembersSoTheServerDecidesWhenItEnds() {
    bindRequest(new Cookie(COOKIE_NAME, "old-token"));
    given(browsers.findUnexpired(hash("old-token")))
        .willReturn(Optional.of(new RememberedBrowser(7L, NOW.plus(Duration.ofDays(365)))));
    given(browsers.rotate(eq(7L), eq(hash("old-token")), any())).willReturn(true);

    thisBrowser.rememberUntil(NOW.plus(Duration.ofDays(90)));

    assertThat(response.getHeader(SET_COOKIE))
        .contains("Max-Age=" + Duration.ofDays(400).toSeconds());
  }

  @Test
  void aLoginThatLostTheRaceToRenewTheTokenLeavesTheWinnersCookieInPlace() {
    bindRequest(new Cookie(COOKIE_NAME, "old-token"));
    Instant until = NOW.plus(Duration.ofDays(365));
    given(browsers.findUnexpired(hash("old-token")))
        .willReturn(Optional.of(new RememberedBrowser(7L, NOW.plus(Duration.ofDays(10)))));
    given(browsers.rotate(eq(7L), eq(hash("old-token")), any())).willReturn(false);

    long id = thisBrowser.rememberUntil(until);

    assertThat(id).isEqualTo(7L);
    assertThat(response.getHeader(SET_COOKIE)).isNull();
    verify(browsers).extendUntil(7L, until);
  }

  @Test
  void claimsAPushLoginStartForThisBrowser() {
    bindRequest(new Cookie(COOKIE_NAME, "token"));
    given(browsers.findUnexpired(hash("token")))
        .willReturn(Optional.of(new RememberedBrowser(7L, NOW.plus(Duration.ofDays(10)))));
    given(browsers.claimLoginStart(7L, SMART_ID_NOTIFICATION, TEN_SECONDS))
        .willReturn(Optional.of(NOW));

    assertThat(thisBrowser.claimLoginStart(SMART_ID_NOTIFICATION)).isEqualTo(NOW);
  }

  @Test
  void refusesAPushLoginStartedTooSoonAfterThePreviousOne() {
    bindRequest(new Cookie(COOKIE_NAME, "token"));
    given(browsers.findUnexpired(hash("token")))
        .willReturn(Optional.of(new RememberedBrowser(7L, NOW.plus(Duration.ofDays(10)))));
    given(browsers.claimLoginStart(7L, SMART_ID_NOTIFICATION, TEN_SECONDS))
        .willReturn(Optional.empty());

    assertThatThrownBy(() -> thisBrowser.claimLoginStart(SMART_ID_NOTIFICATION))
        .isInstanceOf(PushLoginStartedTooSoonException.class);
  }

  @Test
  void refusesAPushLoginFromABrowserThatIsNotRemembered() {
    bindRequest();

    assertThatThrownBy(() -> thisBrowser.claimLoginStart(SMART_ID_NOTIFICATION))
        .isInstanceOf(PushLoginStartedTooSoonException.class);
    verify(browsers, never()).claimLoginStart(anyLong(), any(), any());
  }

  @Test
  void releasesThePushLoginStartOfThisBrowser() {
    bindRequest(new Cookie(COOKIE_NAME, "token"));
    given(browsers.findUnexpired(hash("token")))
        .willReturn(Optional.of(new RememberedBrowser(7L, NOW.plus(Duration.ofDays(10)))));

    thisBrowser.releaseLoginStart(SMART_ID_NOTIFICATION, NOW);

    verify(browsers).releaseLoginStart(7L, SMART_ID_NOTIFICATION, NOW);
  }

  @Test
  void releasingOnABrowserThatIsNotRememberedReleasesNothing() {
    bindRequest();

    thisBrowser.releaseLoginStart(SMART_ID_NOTIFICATION, NOW);

    verify(browsers, never()).releaseLoginStart(anyLong(), any(), any());
  }
}
