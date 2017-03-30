package ee.tuleva.onboarding.auth.session;

import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpSession;
import java.io.Serializable;
import java.util.Optional;

@Component
public class GenericSessionStore {

    public <T extends Serializable> void save(T sessionAttribute) {
        session().setAttribute(sessionAttribute.getClass().getName(), sessionAttribute);
    }

    public <T extends Serializable> Optional<T> get(Class clazz) {
        @SuppressWarnings("unchecked")
        T sessionAttribute = (T) session().getAttribute(clazz.getName());

        if(sessionAttribute == null) {
            return Optional.empty();
        }

        return Optional.of(sessionAttribute);
    }

    private static HttpSession session() {
        ServletRequestAttributes attr = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        boolean allowCreate = true;
        return attr.getRequest().getSession(allowCreate);
    }

}
