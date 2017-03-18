package ee.tuleva.onboarding.auth.mobileid;

import com.codeborne.security.mobileid.MobileIdSignatureSession;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpSession;

@Component
public class MobileIdSignatureSessionStore {

    private static String MOBILE_ID_SESSION_VARIABLE = "mobileIdSignatureSession";

    public  void save(MobileIdSignatureSession mobileIDSession) {
        session().setAttribute(MOBILE_ID_SESSION_VARIABLE, mobileIDSession.toString());
    }

    public MobileIdSignatureSession get() {

        String serializedSession = (String) session().getAttribute(MOBILE_ID_SESSION_VARIABLE);
        if(serializedSession == null) {
            throw new IllegalStateException("No signature session present.");
        }

        return MobileIdSignatureSession.fromString(serializedSession);
    }

    private static HttpSession session() {
        ServletRequestAttributes attr = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        return attr.getRequest().getSession(true); // true == allow create
    }

}
