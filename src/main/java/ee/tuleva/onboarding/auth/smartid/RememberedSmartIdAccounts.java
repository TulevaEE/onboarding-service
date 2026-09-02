package ee.tuleva.onboarding.auth.smartid;

import static ee.tuleva.onboarding.auth.jwt.TokenType.REMEMBERED_SMART_ID_ACCOUNT;
import static org.springframework.http.HttpHeaders.SET_COOKIE;

import ee.tuleva.onboarding.auth.jwt.JwtTokenUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
@Slf4j
public class RememberedSmartIdAccounts {

  static final String COOKIE_NAME = "SMART_ID_REMEMBERED_ACCOUNT";
  private static final String DOCUMENT_NUMBER = "documentNumber";
  private static final String FIRST_NAME = "firstName";
  private static final String LAST_NAME = "lastName";

  private final JwtTokenUtil jwtTokenUtil;
  private final Duration validity;
  private final @Nullable String cookieDomain;

  public RememberedSmartIdAccounts(
      JwtTokenUtil jwtTokenUtil,
      @Value("${smartid.remembered-account-validity:365d}") Duration validity,
      @Value("${server.servlet.session.cookie.domain:#{null}}") @Nullable String cookieDomain) {
    this.jwtTokenUtil = jwtTokenUtil;
    this.validity = validity;
    this.cookieDomain = cookieDomain;
  }

  public void remember(SmartIdPerson person) {
    String token =
        jwtTokenUtil.generateToken(
            REMEMBERED_SMART_ID_ACCOUNT,
            person.getPersonalCode(),
            Map.of(
                DOCUMENT_NUMBER, person.getDocumentNumber(),
                FIRST_NAME, person.getFirstName(),
                LAST_NAME, person.getLastName()),
            validity);
    addCookie(cookie(token).maxAge(validity).build());
  }

  public Optional<RememberedSmartIdAccount> current() {
    Cookie[] cookies = currentRequest().getCookies();
    if (cookies == null) {
      return Optional.empty();
    }
    for (Cookie cookie : cookies) {
      if (COOKIE_NAME.equals(cookie.getName())
          && cookie.getValue() != null
          && !cookie.getValue().isBlank()) {
        return parse(cookie.getValue());
      }
    }
    return Optional.empty();
  }

  public void forget() {
    addCookie(cookie("").maxAge(0).build());
  }

  private Optional<RememberedSmartIdAccount> parse(String token) {
    try {
      Claims claims = jwtTokenUtil.getClaimsFromToken(token, REMEMBERED_SMART_ID_ACCOUNT);
      return Optional.of(
          new RememberedSmartIdAccount(
              claims.getSubject(),
              claims.get(DOCUMENT_NUMBER, String.class),
              claims.get(FIRST_NAME, String.class),
              claims.get(LAST_NAME, String.class)));
    } catch (JwtException | IllegalArgumentException e) {
      log.info(
          "Ignoring invalid remembered Smart-ID account cookie: reason={}",
          e.getClass().getSimpleName());
      return Optional.empty();
    }
  }

  private ResponseCookie.ResponseCookieBuilder cookie(String value) {
    return ResponseCookie.from(COOKIE_NAME, value)
        .httpOnly(true)
        .secure(true)
        .sameSite("Lax")
        .path("/")
        .domain(cookieDomain);
  }

  private void addCookie(ResponseCookie cookie) {
    HttpServletResponse response = requestAttributes().getResponse();
    Objects.requireNonNull(response, "No response to set the remembered Smart-ID account cookie on")
        .addHeader(SET_COOKIE, cookie.toString());
  }

  private static HttpServletRequest currentRequest() {
    return requestAttributes().getRequest();
  }

  private static ServletRequestAttributes requestAttributes() {
    return (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
  }
}
