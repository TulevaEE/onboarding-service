package ee.tuleva.onboarding.aml.sanctions

import tools.jackson.databind.json.JsonMapper
import ee.tuleva.onboarding.auth.principal.PersonImpl
import ee.tuleva.onboarding.country.Countries
import ee.tuleva.onboarding.country.Country
import spock.lang.Specification

class DevSanctionCheckServiceSpec extends Specification {

    JsonMapper objectMapper
    DevSanctionCheckService service

    void setup() {
        objectMapper = JsonMapper.builder().build()
        service = new DevSanctionCheckService(objectMapper)
    }

    def "match returns empty response regardless of input"() {
        given:
        def person = new PersonImpl("38501010002", "John", "Doe")
        def countries = Countries.of("ee")

        when:
        def response = service.match(person, countries)

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
