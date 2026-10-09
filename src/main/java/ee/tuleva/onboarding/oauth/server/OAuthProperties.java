package ee.tuleva.onboarding.oauth.server;

import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

@ConfigurationProperties("oauth")
record OAuthProperties(@Nullable Resource signingKey, Map<String, Client> clients) {

  OAuthProperties(@Nullable Resource signingKey, @Nullable Map<String, Client> clients) {
    this.signingKey = signingKey;
    this.clients = clients == null ? Map.of() : Map.copyOf(clients);
  }

  record Client(
      String name, List<String> redirectUris, List<String> scopes, List<Resource> publicKeys) {}
}
