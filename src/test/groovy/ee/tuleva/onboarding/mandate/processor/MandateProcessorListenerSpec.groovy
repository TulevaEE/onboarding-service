package ee.tuleva.onboarding.mandate.processor

import spock.lang.Specification

import javax.jms.Message

class MandateProcessorListenerSpec extends Specification {

    MandateProcessRepository mandateProcessRepository = Mock(MandateProcessRepository)
    MandateMessageResponseHandler mandateMessageResponseHandler = Mock(MandateMessageResponseHandler)

    MandateProcessorListener service = new MandateProcessorListener(mandateProcessRepository, mandateMessageResponseHandler)

    def "ProcessorListener: On message, persist it"() {
        given:
        MandateProcessResult.ProcessResult sampleProcessResult = MandateProcessResult.ProcessResult.SUCCESS
        String sampleProcessId = "123"

        1 * mandateMessageResponseHandler.getMandateProcessResponse(sampleMessage) >>
                MandateProcessResult.builder()
                        .processId(sampleProcessId)
                        .result(sampleProcessResult)
                        .build()

        1 * mandateProcessRepository.findOneByProcessId(sampleProcessId) >>
                MandateMessageProcess.builder()
                        .processId(sampleProcessId)
                        .build()

        when:
        service.processorListener().onMessage(sampleMessage)
        then:
        1 * mandateProcessRepository.save({MandateMessageProcess process ->
            process.processId == sampleProcessId && process.result.get() == sampleProcessResult.toString()
        })

    }

    Message sampleMessage = Mock(Message)
}
