package ee.tuleva.onboarding.mandate.processor.implementation

import ee.tuleva.onboarding.mandate.processor.MandateProcess
import ee.tuleva.onboarding.mandate.processor.MandateProcessRepository
import ee.tuleva.onboarding.mandate.processor.MandateProcessResult
import ee.tuleva.onboarding.mandate.processor.implementation.MandateMessageResponseHandler
import ee.tuleva.onboarding.mandate.processor.implementation.MandateProcessorListener
import spock.lang.Specification

import javax.jms.Message

class MandateProcessorListenerSpec extends Specification {

    MandateProcessRepository mandateProcessRepository = Mock(MandateProcessRepository)
    MandateMessageResponseHandler mandateMessageResponseHandler = Mock(MandateMessageResponseHandler)

    MandateProcessorListener service = new MandateProcessorListener(mandateProcessRepository, mandateMessageResponseHandler)

    def "ProcessorListener: On message, persist it"() {
        given:
        String sampleProcessId = "123"

        1 * mandateMessageResponseHandler.getMandateProcessResponse(sampleMessage) >>
                MandateProcessResult.builder()
                        .processId(sampleProcessId)
                        .successful(true)
                        .build()

        1 * mandateProcessRepository.findOneByProcessId(sampleProcessId) >>
                MandateProcess.builder()
                        .processId(sampleProcessId)
                        .build()

        when:
        service.processorListener().onMessage(sampleMessage)
        then:
        1 * mandateProcessRepository.save({ MandateProcess process ->
            process.processId == sampleProcessId && process.isSuccessful().get() == true
        })

    }

    Message sampleMessage = Mock(Message)
}
