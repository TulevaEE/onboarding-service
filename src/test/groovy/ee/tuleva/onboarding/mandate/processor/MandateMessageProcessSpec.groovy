package ee.tuleva.onboarding.mandate.processor

import spock.lang.Specification

class MandateMessageProcessSpec extends Specification {

    def "OnCreate: On creation set date"() {
        when:
        MandateProcess mandateProcess = MandateProcess.builder().build()
        mandateProcess.onCreate()
        then:
        mandateProcess.createdDate != null
    }

    def "isSuccessful: test optional"() {
        when:
        MandateProcess mandateProcess = MandateProcess.builder().build()
        then:
        mandateProcess.isSuccessful() == Optional.empty()
    }

}
