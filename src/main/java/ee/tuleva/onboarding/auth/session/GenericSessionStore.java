package ee.tuleva.onboarding.auth.session;

import jakarta.servlet.http.HttpSession;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class GenericSessionStore {

  public <T> void save(T sessionAttribute) {
    HttpSession session = getSession();
    session.setAttribute(sessionAttribute.getClass().getName(), sessionAttribute);
  }

  public <T> Optional<T> get(Class<T> clazz) {
    HttpSession session = getSession();
    @SuppressWarnings("unchecked")
    T sessionAttribute = (T) session.getAttribute(clazz.getName());
    return Optional.ofNullable(sessionAttribute);
  }

  private static HttpSession getSession() {
    ServletRequestAttributes attr =
        (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
    return attr.getRequest().getSession(true);
  }
}
