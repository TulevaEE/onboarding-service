package ee.tuleva.onboarding.auth.smartid;

import static ee.tuleva.onboarding.auth.smartid.RememberedSmartIdAccounts.COOKIE_NAME;
import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.aRememberedAccount;
import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.aSmartIdPerson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.SET_COOKIE;

import ee.tuleva.onboarding.auth.AuthenticatedPersonFixture;
import ee.tuleva.onboarding.auth.KeyStoreFixture;
import ee.tuleva.onboarding.auth.jwt.JwtTokenUtil;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class RememberedSmartIdAccountsTest {

  private final Clock clock = Clock.fixed(Instant.parse("2026-09-02T10:00:00Z"), ZoneOffset.UTC);
  private final JwtTokenUtil jwtTokenUtil =
      new JwtTokenUtil(
          KeyStoreFixture.keyStore(),
          KeyStoreFixture.keyStorePassword,
          "PARTNER AS",
          "TULEVA",
          KeyStoreFixture.getPartnerKeyPair().getPublic(),
          KeyStoreFixture.getPartnerKeyPair().getPublic(),
          clock);
  private final RememberedSmartIdAccounts accounts =
      new RememberedSmartIdAccounts(jwtTokenUtil, Duration.ofDays(365), "tuleva.ee");

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

  private String rememberedCookieValue() {
    String header = Objects.requireNonNull(response.getHeader(SET_COOKIE));
    return header.substring(COOKIE_NAME.length() + 1, header.indexOf(';'));
  }

  @Test
  void rememberSetsALongLivedHardenedCookieOnTheSessionDomain() {
    bindRequest();

    accounts.remember(aSmartIdPerson());

    assertThat(response.getHeader(SET_COOKIE))
        .startsWith(COOKIE_NAME + "=")
        .contains("Max-Age=31536000")
        .contains("Domain=tuleva.ee")
        .contains("Path=/")
        .contains("Secure")
        .contains("HttpOnly")
        .contains("SameSite=Lax");
  }

  @Test
  void currentReadsBackTheRememberedAccount() {
    bindRequest();
    accounts.remember(aSmartIdPerson());
    bindRequest(new Cookie(COOKIE_NAME, rememberedCookieValue()));

    Optional<RememberedSmartIdAccount> current = accounts.current();

    assertThat(current).contains(aRememberedAccount());
  }

  @Test
  void currentIsEmptyWithoutACookie() {
    bindRequest();

    assertThat(accounts.current()).isEmpty();
  }

  @Test
  void currentIgnoresATamperedCookie() {
    bindRequest();
    accounts.remember(aSmartIdPerson());
    bindRequest(new Cookie(COOKIE_NAME, rememberedCookieValue() + "x"));

    assertThat(accounts.current()).isEmpty();
  }

  @Test
  void currentIgnoresAnAccessTokenPresentedAsARememberedAccount() {
    String accessToken =
        jwtTokenUtil.generateAccessToken(
            AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember().build(), List.of());
    bindRequest(new Cookie(COOKIE_NAME, accessToken));

    assertThat(accounts.current()).isEmpty();
  }

  @Test
  void currentIgnoresAnExpiredCookie() {
    bindRequest();
    new RememberedSmartIdAccounts(jwtTokenUtil, Duration.ofDays(-1), "tuleva.ee")
        .remember(aSmartIdPerson());
    bindRequest(new Cookie(COOKIE_NAME, rememberedCookieValue()));

    assertThat(accounts.current()).isEmpty();
  }

  @Test
  void forgetExpiresTheCookie() {
    bindRequest();

    accounts.forget();

    assertThat(response.getHeader(SET_COOKIE))
        .startsWith(COOKIE_NAME + "=;")
        .contains("Max-Age=0")
        .contains("Domain=tuleva.ee");
  }
}
