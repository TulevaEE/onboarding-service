package ee.tuleva.onboarding.auth.session

import spock.lang.Specification
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpSession
import org.springframework.web.context.request.ServletRequestAttributes
import org.springframework.web.context.request.RequestContextHolder

class GenericSessionStoreSpec extends Specification {

  def "ensure serializable attribute round-trip works"() {
    given: "A mock HTTP session and a GenericSessionStore instance"
        MockHttpServletRequest request = new MockHttpServletRequest()
        MockHttpSession session = new MockHttpSession()
        request.setSession(session)
        ServletRequestAttributes attributes = new ServletRequestAttributes(request)
        RequestContextHolder.setRequestAttributes(attributes)
        GenericSessionStore sessionStore = new GenericSessionStore()

    and: "A serializable session attribute"
        String testAttribute = "TestAttribute"

    when: "save is called with the session attribute"
        sessionStore.save(testAttribute)

    then: "The attribute can be retrieved using its class"
        Optional<String> retrievedAttribute = sessionStore.get(String.class)
        retrievedAttribute.isPresent() && retrievedAttribute.get() == testAttribute
  }
}
