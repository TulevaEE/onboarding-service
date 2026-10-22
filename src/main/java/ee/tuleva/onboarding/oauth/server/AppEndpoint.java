package ee.tuleva.onboarding.oauth.server;

import static ee.tuleva.onboarding.oauth.server.ConnectedAppAccess.SCOPE_AUTHORITY_PREFIX;
import static org.springframework.http.HttpMethod.GET;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

record AppEndpoint(HttpMethod method, String path, String scope) {

  static final List<AppEndpoint> OPEN_TO_APPS =
      List.of(
          new AppEndpoint(GET, "/v1/savings-account-statement", "balances:read"),
          new AppEndpoint(GET, "/v1/transactions", "transactions:read"));

  static Optional<AppEndpoint> servedBy(HttpServletRequest request) {
    return OPEN_TO_APPS.stream()
        .filter(endpoint -> endpoint.matcher().matches(request))
        .findFirst();
  }

  void openIn(
      AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry
          authorize) {
    authorize.requestMatchers(matcher()).hasAuthority(SCOPE_AUTHORITY_PREFIX + scope);
  }

  String name() {
    return method.name() + " " + path;
  }

  private RequestMatcher matcher() {
    return PathPatternRequestMatcher.withDefaults().matcher(method, path);
  }
}
