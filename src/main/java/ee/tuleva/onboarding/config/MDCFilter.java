package ee.tuleva.onboarding.config;

import io.sentry.Sentry;
import java.io.IOException;
import java.security.Principal;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.GenericFilterBean;

@Slf4j
@Component
public class MDCFilter extends GenericFilterBean {

  private final String PERSONAL_ID = "personalId";

  @Override
  public void destroy() {}

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {

    boolean successfulRegistration = false;

    HttpServletRequest req = (HttpServletRequest) request;
    Principal principal = req.getUserPrincipal();

    if (principal != null) {
      String personalId = principal.getName();
      successfulRegistration = registerPersonalId(personalId);
    }

    try {
      chain.doFilter(request, response);
    } finally {
      if (successfulRegistration) clearPersonalId();
    }
  }

  private void clearPersonalId() {
    MDC.remove(PERSONAL_ID);
    Sentry.configureScope(scope -> scope.removeTag(PERSONAL_ID));
  }

  private boolean registerPersonalId(String personalId) {
    if (personalId != null && personalId.trim().length() > 0) {
      Sentry.configureScope(scope -> scope.setTag(PERSONAL_ID, personalId));
      MDC.put(PERSONAL_ID, personalId);
      return true;
    }
    return false;
  }
}
