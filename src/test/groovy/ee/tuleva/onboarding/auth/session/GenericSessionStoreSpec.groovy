package ee.tuleva.onboarding.auth.session

import org.springframework.core.convert.support.GenericConversionService
import org.springframework.core.serializer.support.DeserializingConverter
import org.springframework.core.serializer.support.SerializingConverter
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpSession
import org.springframework.session.FindByIndexNameSessionRepository
import org.springframework.session.MapSession
import org.springframework.session.Session
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import spock.lang.Specification

import java.nio.ByteBuffer

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

  def "renewing the id keeps the attributes under a new session id"() {
    given:
    MockHttpServletRequest request = new MockHttpServletRequest()
    request.setSession(new MockHttpSession())
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request))
    sessionStore.save("TestAttribute")
    String idBefore = request.getSession(false).id

    when:
    sessionStore.renewId()

    then:
    request.getSession(false).id != idBefore
    sessionStore.get(String.class) == Optional.of("TestAttribute")

    cleanup:
    RequestContextHolder.resetRequestAttributes()
  }

  def "renewing the id without a session leaves the request without one"() {
    given:
    MockHttpServletRequest request = new MockHttpServletRequest()
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request))

    when:
    sessionStore.renewId()

    then:
    request.getSession(false) == null

    cleanup:
    RequestContextHolder.resetRequestAttributes()
  }

  def "removing an attribute leaves nothing to get"() {
    given:
    MockHttpServletRequest request = new MockHttpServletRequest()
    request.setSession(new MockHttpSession())
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request))
    sessionStore.save("TestAttribute")

    when:
    sessionStore.remove(String.class)

    then:
    sessionStore.get(String.class) == Optional.empty()

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
    long start = System.currentTimeMillis()
    sessionStore.saveBySessionId("missing-session", "stored-value")
    long elapsedMillis = System.currentTimeMillis() - start

    then:
    0 * sessionRepository.save(_)
    3 * sessionRepository.findById("missing-session")
    // 3 failed attempts, each followed by a 100ms backoff sleep.
    elapsedMillis >= 250
  }

  def "saveBySessionId stops retrying and restores the interrupt flag when interrupted"() {
    given:
    sessionRepository.findById("missing-session") >> null
    Thread.currentThread().interrupt()

    when:
    sessionStore.saveBySessionId("missing-session", "stored-value")

    then:
    1 * sessionRepository.findById("missing-session")
    0 * sessionRepository.save(_)
    Thread.currentThread().isInterrupted()

    cleanup:
    Thread.interrupted() // clear the flag so it does not leak into other tests
  }

  def "an attribute stored by an older version of its class reads as absent and is dropped"() {
    given:
    def conversionService = new GenericConversionService()
    conversionService.addConverter(byte[].class, Object.class, new DeserializingConverter())
    MockHttpSession session = new MockHttpSession() {
      @Override
      Object getAttribute(String name) {
        def stored = super.getAttribute(name)
        stored instanceof byte[] ? conversionService.convert(stored, Object) : stored
      }
    }
    session.setAttribute(ArrayList.name, storedByAnOlderVersionOfItsClass(new ArrayList(["in flight"])))
    MockHttpServletRequest request = new MockHttpServletRequest()
    request.setSession(session)
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request))

    when:
    def attribute = sessionStore.get(ArrayList)

    then:
    attribute == Optional.empty()
    session.getAttributeNames().toList().isEmpty()

    cleanup:
    RequestContextHolder.resetRequestAttributes()
  }

  private static byte[] storedByAnOlderVersionOfItsClass(Serializable value) {
    byte[] bytes = new SerializingConverter().convert(value)
    byte[] uid = ByteBuffer.allocate(8).putLong(ObjectStreamClass.lookup(value.class).serialVersionUID).array()
    int at = Collections.indexOfSubList(bytes.toList(), uid.toList())
    bytes[at + 7] = (byte) (bytes[at + 7] ^ 1)
    return bytes
  }
}
