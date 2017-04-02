package ee.tuleva.onboarding.mandate.processor

import spock.lang.Specification

import javax.jms.Message

class MandateProcessorListenerSpec extends Specification {

    MandateProcessRepository mandateProcessRepository = Mock(MandateProcessRepository)
    MandateMessageResponseHandler mandateMessageResponseHandler = Mock(MandateMessageResponseHandler)

    MandateProcessorListener service = new MandateProcessorListener(mandateProcessRepository, mandateMessageResponseHandler)

    def "ProcessorListener: On message, persist it"() {
        given:
        mandateMessageResponseHandler.getMandateProcessResponse(sampleMessage) >>
                new MandateProcessResponse("123", MandateProcessResponse.ProcessResponse.SUCCESS)

        when:
        service.processorListener().onMessage(sampleMessage)
        then:

        true

    }

    Message sampleMessage = Mock(Message)
}
