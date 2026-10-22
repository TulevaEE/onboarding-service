package ee.tuleva.onboarding.oauth.server;

import static ee.tuleva.onboarding.auth.authority.Authority.CONNECTED_APP_ACCESS_TOKEN_TYPE;
import static ee.tuleva.onboarding.auth.authority.Authority.isConnectedAppAccessToken;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.ES384;

import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.util.List;
import org.apache.commons.lang3.Strings;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtAudienceValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtTypeValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

final class AppAccessTokens implements JwtDecoder {

  private static final String BEARER = "Bearer ";

  private final NimbusJwtDecoder decoder;

  AppAccessTokens(JWKSource<SecurityContext> signingKeys, String issuer, Clock clock) {
    var timestamps = new JwtTimestampValidator();
    timestamps.setClock(clock);
    decoder = NimbusJwtDecoder.withJwkSource(signingKeys).jwsAlgorithm(ES384).build();
    decoder.setJwtValidator(
        JwtValidators.createDefaultWithValidators(
            List.of(
                timestamps,
                new JwtTypeValidator(CONNECTED_APP_ACCESS_TOKEN_TYPE),
                new JwtIssuerValidator(issuer),
                new JwtAudienceValidator(AccessTokenClaims.audienceOf(issuer)))));
  }

  static boolean arePresentedBy(HttpServletRequest request) {
    var authorization = request.getHeader(AUTHORIZATION);
    return Strings.CI.startsWith(authorization, BEARER)
        && isConnectedAppAccessToken(authorization.substring(BEARER.length()));
  }

  @Override
  public Jwt decode(String token) {
    return decoder.decode(token);
  }
}
