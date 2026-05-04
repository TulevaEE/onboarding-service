package ee.tuleva.onboarding.auth.session;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
@RequiredArgsConstructor
@Slf4j
public class GenericSessionStore {

  private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;

  public <T> void save(T sessionAttribute) {
    HttpSession session = currentRequest().getSession(true);
    session.setAttribute(sessionAttribute.getClass().getName(), sessionAttribute);
  }

  public <T> Optional<T> get(Class<T> clazz) {
    HttpSession session = currentRequest().getSession(false);
    if (session == null) {
      return Optional.empty();
    }
    @SuppressWarnings("unchecked")
    T sessionAttribute = (T) session.getAttribute(clazz.getName());
    return Optional.ofNullable(sessionAttribute);
  }

  // For background threads with no HttpServletRequest. Bounded retry handles the race where
  // a fast SDK response writes back before the originating request commits the session row.
  public <T> void saveBySessionId(String sessionId, T attribute) {
    saveAttribute(sessionRepository, sessionId, attribute);
  }

  private static <S extends Session> void saveAttribute(
      SessionRepository<S> repository, String sessionId, Object attribute) {
    for (int attempt = 0; attempt < 3; attempt++) {
      S session = repository.findById(sessionId);
      if (session != null) {
        session.setAttribute(attribute.getClass().getName(), attribute);
        repository.save(session);
        return;
      }
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        log.error(
            "Interrupted while waiting for session; dropping attribute: sessionId={}, attribute={}",
            sessionId,
            attribute.getClass().getName());
        return;
      }
    }
    log.error(
        "Session not found after retries; dropping attribute: sessionId={}, attribute={}",
        sessionId,
        attribute.getClass().getName());
  }

  private static HttpServletRequest currentRequest() {
    return ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes())
        .getRequest();
  }
}
