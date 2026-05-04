package ee.tuleva.onboarding.auth.session

import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpSession
import org.springframework.session.FindByIndexNameSessionRepository
import org.springframework.session.MapSession
import org.springframework.session.Session
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import spock.lang.Specification

class GenericSessionStoreSpec extends Specification {

  FindByIndexNameSessionRepository<Session> sessionRepository = Mock()
  GenericSessionStore sessionStore = new GenericSessionStore(sessionRepository)

  def "save and get round-trip works through HttpSession"() {
    given:
    MockHttpServletRequest request = new MockHttpServletRequest()
    request.setSession(new MockHttpSession())
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request))

    when:
    sessionStore.save("TestAttribute")

    then:
    sessionStore.get(String.class) == Optional.of("TestAttribute")

    cleanup:
    RequestContextHolder.resetRequestAttributes()
  }

  def "saveBySessionId stores the attribute on the looked-up session"() {
    given:
    Session session = new MapSession("session-42")
    sessionRepository.findById("session-42") >> session

    when:
    sessionStore.saveBySessionId("session-42", "stored-value")

    then:
    session.getAttribute(String.class.getName()) == "stored-value"
    1 * sessionRepository.save(session)
  }

  def "saveBySessionId retries when the session is not yet available"() {
    given:
    Session session = new MapSession("session-42")
    int callCount = 0
    sessionRepository.findById("session-42") >> {
      callCount++
      callCount < 3 ? null : session
    }

    when:
    sessionStore.saveBySessionId("session-42", "stored-value")

    then:
    callCount == 3
    session.getAttribute(String.class.getName()) == "stored-value"
    1 * sessionRepository.save(session)
  }

  def "saveBySessionId drops the attribute when retries exhaust"() {
    given:
    sessionRepository.findById("missing-session") >> null

    when:
    sessionStore.saveBySessionId("missing-session", "stored-value")

    then:
    0 * sessionRepository.save(_)
  }
}
