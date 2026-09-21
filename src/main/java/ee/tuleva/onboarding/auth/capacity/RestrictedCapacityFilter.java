package ee.tuleva.onboarding.auth.capacity;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.role.ChildRepresentations;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Stops someone whose legal capacity is restricted from transacting in their own name: a person
 * under guardianship, or a minor. Their guardian transacts for them by switching into their role,
 * which still passes because the guardian is then not acting as self.
 *
 * <p>Reading stays open — a restricted person may still see their own account. Restricted capacity
 * makes a transaction void (TsÜS § 10 and § 11 lg 1), and looking at a balance is not a
 * transaction.
 *
 * <p>The block is on the HTTP method rather than on a list of endpoints, so an endpoint added later
 * is covered without anyone remembering this filter.
 */
@Slf4j
@RequiredArgsConstructor
public class RestrictedCapacityFilter extends OncePerRequestFilter {

  private static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "OPTIONS");

  // Not transactions: authenticating, keeping the session alive, switching role and analytics all
  // have to keep working for someone who can still log in and look at their own account.
  private static final Set<String> ALWAYS_ALLOWED =
      Set.of(
          "/authenticate",
          "/idLogin",
          "/login",
          "/oauth/token",
          "/oauth/refresh-token",
          "/v1/tokens",
          "/v1/me/role",
          "/v1/t");

  private final ChildRepresentations childRepresentations;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    Optional<AuthenticatedPerson> blocked = blockedPerson(request);
    if (blocked.isPresent()) {
      log.info(
          "Blocked transaction by person with restricted legal capacity: personalCode={}, method={}, path={}",
          blocked.get().getPersonalCode(),
          request.getMethod(),
          request.getRequestURI());
      respondForbidden(response);
      return;
    }

    filterChain.doFilter(request, response);
  }

  private Optional<AuthenticatedPerson> blockedPerson(HttpServletRequest request) {
    if (READ_METHODS.contains(request.getMethod())
        || ALWAYS_ALLOWED.contains(request.getRequestURI())) {
      return Optional.empty();
    }

    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null
        || !(authentication.getPrincipal() instanceof AuthenticatedPerson person)) {
      return Optional.empty();
    }

    // A guardian acting for the person they represent is exactly what should still work.
    if (!person.isActingAsSelf()) {
      return Optional.empty();
    }

    return childRepresentations.hasRestrictedLegalCapacity(person.getPersonalCode())
        ? Optional.of(person)
        : Optional.empty();
  }

  private static void respondForbidden(HttpServletResponse response) throws IOException {
    Map<String, String> error =
        Map.of(
            "error",
            "RESTRICTED_LEGAL_CAPACITY",
            "error_description",
            "Person has restricted legal capacity and cannot transact in their own name");
    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
    response.setContentType("application/json");
    response.getWriter().write(JsonMapper.builder().build().writeValueAsString(error));
  }
}
