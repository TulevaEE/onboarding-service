package ee.tuleva.onboarding.auth;

import static org.springframework.http.HttpHeaders.USER_AGENT;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Where a request reached us from. Recorded on login so an attack can be spotted while it is
 * happening and reconstructed afterwards, which Smart-ID asks relying parties to be able to do.
 */
@Component
public class ClientConnection {

  private static final int MAX_USER_AGENT_LENGTH = 200;

  public Optional<String> ipAddress() {
    return currentRequest().map(HttpServletRequest::getRemoteAddr).filter(ip -> !ip.isBlank());
  }

  public Optional<String> userAgent() {
    return currentRequest()
        .map(request -> request.getHeader(USER_AGENT))
        .filter(userAgent -> !userAgent.isBlank())
        .map(ClientConnection::truncated);
  }

  private static String truncated(String userAgent) {
    return userAgent.length() <= MAX_USER_AGENT_LENGTH
        ? userAgent
        : userAgent.substring(0, MAX_USER_AGENT_LENGTH);
  }

  private static Optional<HttpServletRequest> currentRequest() {
    @Nullable RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
    if (attributes instanceof ServletRequestAttributes servletAttributes) {
      return Optional.of(servletAttributes.getRequest());
    }
    return Optional.empty();
  }
}
