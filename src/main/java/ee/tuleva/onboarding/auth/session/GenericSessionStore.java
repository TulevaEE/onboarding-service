package ee.tuleva.onboarding.auth.session;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class GenericSessionStore {

  private static final String GENERIC_SESSION_STORE_ATTRIBUTES =
      GenericSessionStore.class.getName() + ".attributes";

  public <T extends Serializable> void save(T sessionAttribute) {
    getSessionAttributes().put(sessionAttribute.getClass().getName(), sessionAttribute);
  }

  public <T extends Serializable> Optional<T> get(Class<?> clazz) {
    @SuppressWarnings("unchecked")
    T sessionAttribute = (T) getSessionAttributes().get(clazz.getName());

    if (sessionAttribute == null) {
      return Optional.empty();
    }

    return Optional.of(sessionAttribute);
  }

  private static <T extends Serializable> Map<String, T> getSessionAttributes() {
    @SuppressWarnings("unchecked")
    Map<String, T> attributes =
        (Map<String, T>) getSession().getAttribute(GENERIC_SESSION_STORE_ATTRIBUTES);
    if (attributes == null) {
      attributes = new HashMap<>();
      getSession().setAttribute(GENERIC_SESSION_STORE_ATTRIBUTES, attributes);
    }
    return attributes;
  }

  private static HttpSession getSession() {
    ServletRequestAttributes attr =
        (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
    return attr.getRequest().getSession(true);
  }
}
