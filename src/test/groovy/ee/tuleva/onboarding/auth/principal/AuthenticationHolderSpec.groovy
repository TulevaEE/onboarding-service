package ee.tuleva.onboarding.auth.principal

import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember

class AuthenticationHolderSpec extends Specification {

  def "getAuthenticatedPerson should return AuthenticatedPerson when authentication is valid"() {
    given:
        AuthenticationHolder authenticationHolder = new AuthenticationHolder()
        Authentication authentication = Mock(Authentication)
        SecurityContext securityContext = Mock(SecurityContext)
        AuthenticatedPerson authenticatedPerson = sampleAuthenticatedPersonAndMember().build()

        SecurityContextHolder.setContext(securityContext)
        securityContext.getAuthentication() >> authentication
        authentication.getPrincipal() >> authenticatedPerson

    when:
        AuthenticatedPerson result = authenticationHolder.getAuthenticatedPerson()

    then:
        result == authenticatedPerson
  }

  def "getAuthenticatedPerson should throw RuntimeException when authentication is null"() {
    given:
        AuthenticationHolder authenticationHolder = new AuthenticationHolder()
        SecurityContext securityContext = Mock(SecurityContext)

        SecurityContextHolder.setContext(securityContext)
        securityContext.getAuthentication() >> null

    when:
        authenticationHolder.getAuthenticatedPerson()

    then:
        thrown(RuntimeException)
  }

  def "getAuthenticatedPerson should throw RuntimeException when principal is not AuthenticatedPerson"() {
    given:
        AuthenticationHolder authenticationHolder = new AuthenticationHolder()
        Authentication authentication = Mock(Authentication)
        SecurityContext securityContext = Mock(SecurityContext)

        SecurityContextHolder.setContext(securityContext)
        securityContext.getAuthentication() >> authentication
        authentication.getPrincipal() >> new Object()

    when:
        authenticationHolder.getAuthenticatedPerson()

    then:
        thrown(RuntimeException)
  }
}
