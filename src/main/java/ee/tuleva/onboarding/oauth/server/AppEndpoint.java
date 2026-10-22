package ee.tuleva.onboarding.oauth.server;

import static ee.tuleva.onboarding.oauth.server.ConnectedAppAuthenticator.SCOPE_AUTHORITY_PREFIX;
import static org.springframework.http.HttpMethod.GET;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Optional;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

record AppEndpoint(String name, RequestMatcher matcher, String authority) {

  static final List<AppEndpoint> OPEN_TO_APPS =
      List.of(
          readable("/v1/savings-account-statement", "balances:read"),
          readable("/v1/transactions", "transactions:read"));

  static Optional<AppEndpoint> servedBy(HttpServletRequest request) {
    return OPEN_TO_APPS.stream()
        .filter(endpoint -> endpoint.matcher().matches(request))
        .findFirst();
  }

  private static AppEndpoint readable(String path, String scope) {
    return new AppEndpoint(
        GET.name() + " " + path,
        PathPatternRequestMatcher.withDefaults().matcher(GET, path),
        SCOPE_AUTHORITY_PREFIX + scope);
  }
}
