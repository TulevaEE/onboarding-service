package ee.tuleva.onboarding.oauth.server;

import static ee.tuleva.onboarding.event.TrackableEventType.APP_DATA_READ;
import static java.util.Objects.requireNonNull;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.event.TrackableEvent;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

@RequiredArgsConstructor
class AppReadLog extends OncePerRequestFilter {

  private final ApplicationEventPublisher eventPublisher;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    var authentication =
        requireNonNull(
            SecurityContextHolder.getContext().getAuthentication(),
            "App read without an authenticated app");
    var access = (ConnectedAppAccess) requireNonNull(authentication.getDetails());
    var endpoint =
        AppEndpoint.servedBy(request)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "App read outside the endpoints open to apps: method="
                            + request.getMethod()));
    Map<String, @Nullable Object> read =
        Map.of(
            "clientId", access.clientId(),
            "grantId", access.grantId().toString(),
            "endpoint", endpoint.name(),
            "scopes", access.scopes());
    eventPublisher.publishEvent(
        new TrackableEvent(
            (AuthenticatedPerson) requireNonNull(authentication.getPrincipal()),
            APP_DATA_READ,
            read));
    filterChain.doFilter(request, response);
  }
}
