package ee.tuleva.onboarding.auth.principal

import spock.lang.Specification

//Used as cross service principal
class AuthenticatedPersonSpec extends Specification {
    def "ToString"() {
        when:
        AuthenticatedPerson authenticatedPerson = AuthenticatedPerson.builder()
        .personalCode("1212").build()
        then:
        authenticatedPerson.toString() == authenticatedPerson.personalCode
    }
}
