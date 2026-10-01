package ee.tuleva.onboarding.auth.smartid;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.springframework.http.HttpHeaders.SET_COOKIE;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
@Slf4j
public class RememberedSmartIdAccounts {

  static final String COOKIE_NAME = "__Host-SMART_ID_REMEMBERED_BROWSER";
  private static final int TOKEN_BYTES = 32;

  private final RememberedBrowsers browsers;
  private final Duration validity;
  private final Clock clock;
  private final SecureRandom random = new SecureRandom();

  public RememberedSmartIdAccounts(
      RememberedBrowsers browsers,
      @Value("${smartid.remembered-browser-validity:90d}") Duration validity,
      Clock clock) {
    this.browsers = browsers;
    this.validity = validity;
    this.clock = clock;
  }

  public Optional<RememberedSmartIdAccount> current() {
    return currentBrowser().map(RememberedBrowser::toAccount);
  }

  public void remember(SmartIdPerson person, boolean deviceLinkVerified) {
    Optional<Instant> verifiedAt =
        deviceLinkVerified ? Optional.of(Instant.now(clock)) : earlierDeviceLinkVerification();
    verifiedAt.ifPresent(at -> rememberVerifiedAt(person, at));
  }

  private Optional<Instant> earlierDeviceLinkVerification() {
    return currentBrowser().map(RememberedBrowser::verifiedAt);
  }

  private void rememberVerifiedAt(SmartIdPerson person, Instant verifiedAt) {
    cookieToken().ifPresent(token -> browsers.remove(hash(token)));

    String token = newToken();
    browsers.add(
        hash(token),
        new RememberedBrowser(
            person.getPersonalCode(),
            person.getDocumentNumber(),
            person.getFirstName(),
            person.getLastName(),
            verifiedAt),
        verifiedAt.plus(validity));
    addCookie(
        cookie(token).maxAge(Duration.between(Instant.now(clock), verifiedAt.plus(validity))));
  }

  public void forget() {
    cookieToken().ifPresent(token -> browsers.remove(hash(token)));
    expireCookie();
  }

  public void forgetEverywhere() {
    currentBrowser()
        .ifPresent(
            browser -> {
              int forgotten = browsers.removeAllOf(browser.personalCode());
              log.info("Forgot every remembered Smart-ID browser: forgotten={}", forgotten);
            });
    expireCookie();
  }

  public void claimNotificationLoginStart() {
    Duration minimumIntervalBetweenPushesFromOneBrowser = Duration.ofSeconds(30);
    boolean claimed =
        cookieToken()
            .map(
                token ->
                    browsers.claimNotificationLoginStart(
                        hash(token), minimumIntervalBetweenPushesFromOneBrowser))
            .orElse(false);
    if (!claimed) {
      throw new NotificationLoginStartedTooSoonException();
    }
  }

  private Optional<RememberedBrowser> currentBrowser() {
    return cookieToken().flatMap(token -> browsers.findUnexpired(hash(token)));
  }

  private Optional<String> cookieToken() {
    Cookie[] cookies = currentRequest().getCookies();
    if (cookies == null) {
      return Optional.empty();
    }
    for (Cookie cookie : cookies) {
      if (COOKIE_NAME.equals(cookie.getName())
          && cookie.getValue() != null
          && !cookie.getValue().isBlank()) {
        return Optional.of(cookie.getValue());
      }
    }
    return Optional.empty();
  }

  private String newToken() {
    byte[] token = new byte[TOKEN_BYTES];
    random.nextBytes(token);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
  }

  static String hash(String token) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(UTF_8));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }

  private void expireCookie() {
    addCookie(cookie("").maxAge(Duration.ZERO));
  }

  private ResponseCookie.ResponseCookieBuilder cookie(String value) {
    return ResponseCookie.from(COOKIE_NAME, value)
        .httpOnly(true)
        .secure(true)
        .sameSite("Lax")
        .path("/");
  }

  private void addCookie(ResponseCookie.ResponseCookieBuilder cookie) {
    HttpServletResponse response = requestAttributes().getResponse();
    Objects.requireNonNull(response, "No response to set the remembered Smart-ID browser cookie on")
        .addHeader(SET_COOKIE, cookie.build().toString());
  }

  private static HttpServletRequest currentRequest() {
    return requestAttributes().getRequest();
  }

  private static ServletRequestAttributes requestAttributes() {
    return (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
  }
}
