package ee.tuleva.onboarding.auth.principal

import ee.tuleva.onboarding.auth.role.Role
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.role.RoleType.*

class AuthenticatedPersonSpec extends Specification {

    def "ToString"() {
        when:
        AuthenticatedPerson authenticatedPerson = AuthenticatedPerson.builder()
            .personalCode("1212").build()
        then:
        authenticatedPerson.toString() == authenticatedPerson.personalCode
    }

    def "role is null when not set in builder"() {
        when:
        def person = AuthenticatedPerson.builder()
            .personalCode("38501010000")
            .firstName("John")
            .lastName("Doe")
            .build()
        then:
        person.role == null
    }

    def "role can be set to company"() {
        given:
        def company = new Role(LEGAL_ENTITY, "12345678", "Test Company")
        when:
        def person = AuthenticatedPerson.builder()
            .personalCode("38501010000")
            .firstName("John")
            .lastName("Doe")
            .role(company)
            .build()
        then:
        person.role == company
        person.role.code == "12345678"
    }
}
