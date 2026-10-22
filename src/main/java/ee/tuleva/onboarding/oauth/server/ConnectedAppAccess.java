package ee.tuleva.onboarding.oauth.server;

import java.util.List;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

record ConnectedAppAccess(String clientId, UUID grantId, List<String> scopes) {

  static final String SCOPE_AUTHORITY_PREFIX = "SCOPE_";

  List<GrantedAuthority> authorities() {
    return scopes.stream()
        .<GrantedAuthority>map(scope -> new SimpleGrantedAuthority(SCOPE_AUTHORITY_PREFIX + scope))
        .toList();
  }
}
