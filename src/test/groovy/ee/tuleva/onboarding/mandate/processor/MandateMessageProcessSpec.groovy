package ee.tuleva.onboarding.mandate.processor

import spock.lang.Specification

class MandateMessageProcessSpec extends Specification {

    def "OnCreate: On creation set date"() {
        when:
        MandateMessageProcess mandateProcess = MandateMessageProcess.builder().build()
        mandateProcess.onCreate()
        then:
        mandateProcess.createdDate != null
    }

    def "getResult: test optional"() {
        when:
        MandateMessageProcess mandateProcess = MandateMessageProcess.builder().build()
        then:
        mandateProcess.getResult() == Optional.empty()
    }

}
