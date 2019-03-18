package ee.tuleva.onboarding.auth.smartid

import ee.sk.smartid.AuthenticationHash
import spock.lang.Specification

class SmartIdAuthenticationHashGeneratorSpec extends Specification {

    SmartIdAuthenticationHashGenerator generator = new SmartIdAuthenticationHashGenerator()

    def "Generates hashes"() {
        when:
        AuthenticationHash hash = generator.generateHash()
        then:
        hash.calculateVerificationCode().length() == 4
    }
}
