package ee.tuleva.onboarding.config;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import ee.tuleva.onboarding.config.ReadOnlyForWards.WardIsReadOnly;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
class ReadOnlyAccessDeniedHandler implements AccessDeniedHandler {

  private static final Map<String, String> READ_ONLY_ACCESS =
      Map.of(
          "error",
          "READ_ONLY_ACCESS",
          "error_description",
          "A person with restricted legal capacity can only view their account");

  private final AccessDeniedHandler otherwise = new AccessDeniedHandlerImpl();
  private final JsonMapper jsonMapper = JsonMapper.builder().build();

  @Override
  public void handle(
      HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
      throws IOException, ServletException {
    if (!isWardRefusal(exception)) {
      otherwise.handle(request, response, exception);
      return;
    }
    log.info(
        "Refused a ward's transaction: method={}, path={}",
        request.getMethod(),
        request.getRequestURI());
    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
    response.setContentType(APPLICATION_JSON_VALUE);
    response.getWriter().write(jsonMapper.writeValueAsString(READ_ONLY_ACCESS));
  }

  private static boolean isWardRefusal(AccessDeniedException exception) {
    return exception instanceof AuthorizationDeniedException denied
        && denied.getAuthorizationResult() instanceof WardIsReadOnly;
  }
}
