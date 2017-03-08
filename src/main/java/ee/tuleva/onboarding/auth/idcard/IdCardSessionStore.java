package ee.tuleva.onboarding.auth.idcard;

import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpSession;
import java.util.Optional;

@Component
public class IdCardSessionStore {

    private static String ID_CARD_SESSION = "idCardSession";

    public  void save(IdCardSession idCardSession) {
        session().setAttribute(ID_CARD_SESSION, idCardSession.toString());
    }

    public Optional<IdCardSession> get() {
        String serializedSession = (String) session().getAttribute(ID_CARD_SESSION);
        if(serializedSession == null) {
            return Optional.empty();
        }

        return Optional.of(IdCardSession.fromString(serializedSession));
    }

    private static HttpSession session() {
        ServletRequestAttributes attr = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        boolean allowCreate = true;
        return attr.getRequest().getSession(allowCreate);
    }

}
