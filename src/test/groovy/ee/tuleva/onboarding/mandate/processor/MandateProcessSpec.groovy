package ee.tuleva.onboarding.mandate.processor

import spock.lang.Specification

class MandateProcessSpec extends Specification {
    def "OnCreate: On creation set date"() {
        when:
        MeandateMessageProcess mandateProcess = MeandateMessageProcess.builder().build()
        mandateProcess.onCreate()
        then:
        mandateProcess.createdDate != null
    }}
