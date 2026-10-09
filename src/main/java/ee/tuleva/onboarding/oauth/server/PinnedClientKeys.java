package ee.tuleva.onboarding.oauth.server;

import static java.util.stream.Collectors.toUnmodifiableMap;
import static org.springframework.security.oauth2.server.authorization.authentication.JwtClientAssertionDecoderFactory.DEFAULT_JWT_VALIDATOR_FACTORY;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.stereotype.Component;

@Component
class PinnedClientKeys implements JwtDecoderFactory<RegisteredClient> {

  static final Duration CLIENT_ASSERTION_MAX_LIFETIME = Duration.ofSeconds(60);
  static final Duration CLOCK_SKEW = Duration.ofSeconds(60);

  private final Map<String, List<RSAPublicKey>> keysByClientId;
  private final Clock clock;

  PinnedClientKeys(OAuthProperties properties, Clock clock) {
    this.clock = clock;
    this.keysByClientId =
        properties.clients().entrySet().stream()
            .collect(
                toUnmodifiableMap(
                    Map.Entry::getKey,
                    entry ->
                        entry.getValue().publicKeys().stream().map(RsaPem::publicKey).toList()));
  }

  @Override
  public JwtDecoder createDecoder(RegisteredClient client) {
    var keys = keysByClientId.get(client.getClientId());
    if (keys == null) {
      throw new IllegalStateException(
          "No pinned public keys for client: clientId=" + client.getClientId());
    }
    var processor = new DefaultJWTProcessor<SecurityContext>();
    processor.setJWSKeySelector(
        (header, context) -> JWSAlgorithm.RS256.equals(header.getAlgorithm()) ? keys : List.of());
    var decoder = new NimbusJwtDecoder(processor);
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            DEFAULT_JWT_VALIDATOR_FACTORY.apply(client), this::issuedJustNowAndShortLived));
    return decoder;
  }

  private OAuth2TokenValidatorResult issuedJustNowAndShortLived(Jwt assertion) {
    var issuedAt = assertion.getIssuedAt();
    var expiresAt = assertion.getExpiresAt();
    var latestAcceptable = clock.instant().plus(CLOCK_SKEW);
    if (issuedAt == null
        || expiresAt == null
        || issuedAt.isAfter(latestAcceptable)
        || expiresAt.isAfter(latestAcceptable.plus(CLIENT_ASSERTION_MAX_LIFETIME))
        || Duration.between(issuedAt, expiresAt).compareTo(CLIENT_ASSERTION_MAX_LIFETIME) > 0) {
      return OAuth2TokenValidatorResult.failure(
          new OAuth2Error(
              "invalid_client",
              "Client assertion must be issued now and live at most 60 seconds",
              null));
    }
    return OAuth2TokenValidatorResult.success();
  }
}
