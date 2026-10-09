package ee.tuleva.onboarding.oauth.server;

import static java.util.Objects.requireNonNull;
import static org.springframework.security.oauth2.server.authorization.OAuth2TokenType.ACCESS_TOKEN;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class AccessTokenClaims implements OAuth2TokenCustomizer<JwtEncodingContext> {

  static final String CONNECT_API_PATH = "/connect/v1";
  static final String GRANT_ID_CLAIM = "grant_id";

  private final AuthorizationServerSettings settings;

  @Override
  public void customize(JwtEncodingContext context) {
    if (ACCESS_TOKEN.equals(context.getTokenType())) {
      context
          .getClaims()
          .audience(List.of(requireNonNull(settings.getIssuer()) + CONNECT_API_PATH))
          .claim(GRANT_ID_CLAIM, requireNonNull(context.getAuthorization()).getId());
    }
  }
}
