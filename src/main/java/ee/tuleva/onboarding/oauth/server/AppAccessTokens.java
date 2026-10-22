package ee.tuleva.onboarding.oauth.server;

import static ee.tuleva.onboarding.oauth.server.AccessTokenClaims.ACCESS_TOKEN_TYPE;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.ES384;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import jakarta.servlet.http.HttpServletRequest;
import java.text.ParseException;
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
                new JwtTypeValidator(ACCESS_TOKEN_TYPE),
                new JwtIssuerValidator(issuer),
                new JwtAudienceValidator(AccessTokenClaims.audienceOf(issuer)))));
  }

  static boolean arePresentedBy(HttpServletRequest request) {
    var authorization = request.getHeader(AUTHORIZATION);
    return Strings.CI.startsWith(authorization, BEARER)
        && looksLikeAnAccessToken(authorization.substring(BEARER.length()));
  }

  private static boolean looksLikeAnAccessToken(String token) {
    try {
      var header = JWSObject.parse(token).getHeader();
      return JWSAlgorithm.ES384.equals(header.getAlgorithm())
          && new JOSEObjectType(ACCESS_TOKEN_TYPE).equals(header.getType());
    } catch (ParseException notASignedToken) {
      return false;
    }
  }

  @Override
  public Jwt decode(String token) {
    return decoder.decode(token);
  }
}
