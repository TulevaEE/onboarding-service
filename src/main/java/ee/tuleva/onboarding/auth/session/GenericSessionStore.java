package ee.tuleva.onboarding.auth.session;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;

@Component
@SessionScope
public class GenericSessionStore implements Serializable {

  @Serial private static final long serialVersionUID = -648103071415508424L;

  private final Map<String, Object> sessionAttributes = new HashMap<>();

  public <T extends Serializable> void save(T sessionAttribute) {
    sessionAttributes.put(sessionAttribute.getClass().getName(), sessionAttribute);
  }

  public <T extends Serializable> Optional<T> get(Class clazz) {
    @SuppressWarnings("unchecked")
    T sessionAttribute = (T) sessionAttributes.get(clazz.getName());

    if (sessionAttribute == null) {
      return Optional.empty();
    }

    return Optional.of(sessionAttribute);
  }
}
