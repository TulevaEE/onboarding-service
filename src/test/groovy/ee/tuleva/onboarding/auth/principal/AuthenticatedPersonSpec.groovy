package ee.tuleva.onboarding.auth.principal

import spock.lang.Specification

class AuthenticatedPersonSpec extends Specification {

    def "ToString"() {
        when:
        AuthenticatedPerson authenticatedPerson = AuthenticatedPerson.builder()
            .personalCode("1212").build()
        then:
        authenticatedPerson.toString() == authenticatedPerson.personalCode
    }

    def "getActingAs defaults to self when not set in builder"() {
        when:
        def person = AuthenticatedPerson.builder()
            .personalCode("38501010000")
            .firstName("Jordan")
            .lastName("Valdma")
            .build()
        then:
        person.actingAs instanceof ActingAs.Person
        person.actingAs.code() == "38501010000"
    }

    def "actingAs can be set to company"() {
        when:
        def person = AuthenticatedPerson.builder()
            .personalCode("38501010000")
            .firstName("Jordan")
            .lastName("Valdma")
            .actingAs(new ActingAs.Company("12345678"))
            .build()
        then:
        person.actingAs instanceof ActingAs.Company
        person.actingAs.code() == "12345678"
    }

    def "getActingAs returns explicitly set actingAs"() {
        given:
        def company = new ActingAs.Company("12345678")
        when:
        def person = AuthenticatedPerson.builder()
            .personalCode("38501010000")
            .firstName("Jordan")
            .lastName("Valdma")
            .actingAs(company)
            .build()
        then:
        person.actingAs == company
    }
}
