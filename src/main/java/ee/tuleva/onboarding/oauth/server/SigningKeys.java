package ee.tuleva.onboarding.oauth.server;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
class SigningKeys implements JWKSource<SecurityContext> {

  private final JWKSet keys;

  SigningKeys(OAuthProperties properties) {
    this.keys = keysOf(properties);
  }

  @Override
  public List<JWK> get(JWKSelector selector, @Nullable SecurityContext context) {
    return selector.select(keys);
  }

  private static JWKSet keysOf(OAuthProperties properties) {
    var signingKey = properties.signingKey();
    if (signingKey == null) {
      if (!properties.clients().isEmpty()) {
        throw new IllegalStateException(
            "Connected apps need a token signing key: clients=" + properties.clients().keySet());
      }
      return new JWKSet();
    }
    var privateKey = RsaPem.privateKey(signingKey);
    try {
      return new JWKSet(
          new RSAKey.Builder(RsaPem.publicKeyOf(privateKey))
              .privateKey(privateKey)
              .keyIDFromThumbprint()
              .build());
    } catch (JOSEException e) {
      throw new IllegalStateException("Cannot build the token signing key", e);
    }
  }
}
