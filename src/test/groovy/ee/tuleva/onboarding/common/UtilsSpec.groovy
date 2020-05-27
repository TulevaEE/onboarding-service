package ee.tuleva.onboarding.common

import spock.lang.Specification

import java.security.InvalidParameterException
import java.time.Instant

class UtilsSpec extends Specification {

    def "bad input will throw exception for parsing Instant date"() {
        when:
        Utils.parseInstant("Some-bad-input-for-date")
        then:
        thrown InvalidParameterException
    }

    def "good input will parse a date that was created before today"() {
        given:
        Instant first = Instant.now();
        when:
        def result = Utils.parseInstant("2020-05-20")
        then:
        first.isAfter(result)
    }

}
