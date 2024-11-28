package ee.tuleva.onboarding.aml.sanctions

import com.fasterxml.jackson.databind.ObjectMapper
import ee.tuleva.onboarding.auth.principal.PersonImpl
import ee.tuleva.onboarding.user.address.Address
import spock.lang.Specification

class DevSanctionCheckServiceSpec extends Specification {

    ObjectMapper objectMapper
    DevSanctionCheckService service

    void setup() {
        objectMapper = new ObjectMapper()
        service = new DevSanctionCheckService(objectMapper)
    }

    def "match returns empty response regardless of input"() {
        given:
        def person = new PersonImpl("38501010002", "John", "Doe")
        def address = Address.builder().countryCode("ee").build()

        when:
        def response = service.match(person, address)

        then:
        response.results().isEmpty()
        response.query().isEmpty()
    }

    def "match returns empty response for null address"() {
        given:
        def person = new PersonImpl("38501010002", "John", "Doe")

        when:
        def response = service.match(person, null)

        then:
        response.results().isEmpty()
        response.query().isEmpty()
    }
} 
