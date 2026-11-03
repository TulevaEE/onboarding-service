package ee.tuleva.onboarding.oauth.server;

import static org.springframework.http.HttpHeaders.SET_COOKIE;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class ConnectEntryPoint implements AuthenticationEntryPoint {

  static final String BROWSER_BINDING_COOKIE = "oauth_connect";
  static final String BROWSER_BINDING_COOKIE_PATH = "/v1/connect";

  private final PendingAuthorizations pendingAuthorizations;

  @Value("${frontend.url}")
  private final String frontendUrl;

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException)
      throws IOException {
    if (!(request.getAttribute(OAuth2AuthorizationCodeRequestAuthenticationToken.class.getName())
        instanceof OAuth2AuthorizationCodeRequestAuthenticationToken validatedRequest)) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST);
      return;
    }
    var started = pendingAuthorizations.start(validatedRequest);
    response.addHeader(
        SET_COOKIE,
        ResponseCookie.from(BROWSER_BINDING_COOKIE, started.browserBinding())
            .httpOnly(true)
            .secure(true)
            .sameSite("Strict")
            .path(BROWSER_BINDING_COOKIE_PATH)
            .maxAge(PendingAuthorizations.LIFETIME)
            .build()
            .toString());
    response.sendRedirect(frontendUrl + "/connect/" + started.id());
  }
}
