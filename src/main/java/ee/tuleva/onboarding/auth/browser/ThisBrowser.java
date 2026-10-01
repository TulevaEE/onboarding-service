package ee.tuleva.onboarding.auth.browser;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.springframework.http.HttpHeaders.SET_COOKIE;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
@RequiredArgsConstructor
public class ThisBrowser {

  public static final String COOKIE_NAME = "__Host-REMEMBERED_BROWSER";
  private static final int TOKEN_BYTES = 32;
  private static final Duration MINIMUM_INTERVAL_BETWEEN_PUSH_LOGINS_FROM_ONE_BROWSER =
      Duration.ofSeconds(30);

  private final RememberedBrowsers browsers;
  private final Clock clock;
  private final SecureRandom random = new SecureRandom();

  public Optional<RememberedBrowser> remembered() {
    return cookieToken().flatMap(token -> browsers.findUnexpired(hash(token)));
  }

  public long rememberUntil(Instant until) {
    Optional<RememberedBrowser> current = remembered();
    Instant expiresAt =
        current.map(RememberedBrowser::expiresAt).filter(until::isBefore).orElse(until);
    String token = newToken();
    long id =
        current
            .map(
                browser -> {
                  browsers.rotate(browser.id(), hash(token), expiresAt);
                  return browser.id();
                })
            .orElseGet(() -> browsers.add(hash(token), expiresAt));
    addCookie(cookie(token).maxAge(Duration.between(Instant.now(clock), expiresAt)));
    return id;
  }

  public void forget() {
    remembered().ifPresent(browser -> browsers.remove(browser.id()));
    addCookie(cookie("").maxAge(Duration.ZERO));
  }

  public void claimLoginStart(PushLogin pushLogin) {
    boolean claimed =
        remembered()
            .map(
                browser ->
                    browsers.claimLoginStart(
                        browser.id(),
                        pushLogin,
                        MINIMUM_INTERVAL_BETWEEN_PUSH_LOGINS_FROM_ONE_BROWSER))
            .orElse(false);
    if (!claimed) {
      throw new PushLoginStartedTooSoonException(pushLogin);
    }
  }

  private Optional<String> cookieToken() {
    Cookie[] cookies = requestAttributes().getRequest().getCookies();
    if (cookies == null) {
      return Optional.empty();
    }
    return Arrays.stream(cookies)
        .filter(cookie -> COOKIE_NAME.equals(cookie.getName()))
        .map(Cookie::getValue)
        .filter(value -> value != null && !value.isBlank())
        .findFirst();
  }

  private String newToken() {
    byte[] token = new byte[TOKEN_BYTES];
    random.nextBytes(token);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
  }

  public static String hash(String token) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(UTF_8));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }

  private static ResponseCookie.ResponseCookieBuilder cookie(String value) {
    return ResponseCookie.from(COOKIE_NAME, value)
        .httpOnly(true)
        .secure(true)
        .sameSite("Lax")
        .path("/");
  }

  private static void addCookie(ResponseCookie.ResponseCookieBuilder cookie) {
    HttpServletResponse response = requestAttributes().getResponse();
    Objects.requireNonNull(response, "No response to set the remembered browser cookie on")
        .addHeader(SET_COOKIE, cookie.build().toString());
  }

  private static ServletRequestAttributes requestAttributes() {
    return (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
  }
}
