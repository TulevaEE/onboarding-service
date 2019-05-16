package ee.tuleva.onboarding.common

import spock.lang.Specification

import java.security.InvalidParameterException

class UtilsSpec extends Specification {

    def "bad input will throw exception for parsing Instant date"() {
        when:
          Utils.parseInstant("Some-bad-input-for-date")
        then:
        thrown InvalidParameterException
    }

}
