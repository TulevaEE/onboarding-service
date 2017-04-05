package ee.tuleva.onboarding.mandate.processor.implementation

import ee.tuleva.onboarding.mandate.MandateApplicationType
import ee.tuleva.onboarding.mandate.content.MandateXmlMessage
import ee.tuleva.onboarding.mandate.processor.MandateProcess
import ee.tuleva.onboarding.mandate.processor.MandateProcessRepository
import org.springframework.jms.core.JmsTemplate
import spock.lang.Specification

class MhubProcessRunnerSpec extends Specification {

    JmsTemplate jmsTemplate = Mock(JmsTemplate)
    MandateProcessRepository mandateProcessRepository = Mock(MandateProcessRepository)

    MhubProcessRunner service = new MhubProcessRunner(jmsTemplate, mandateProcessRepository)

    String sampleProcessId1 = "123"
    String sampleProcessId2 = "124"

    def "process: process messages synchronously: finish one message and then start another"() {
        given:
        2 * jmsTemplate.send("MHUB.PRIVATE.IN", _)
        2 * mandateProcessRepository.findOneByProcessId(sampleProcessId1) >>
                MandateProcess.builder()
                        .processId(sampleProcessId1)
                        .successful(true)
                        .build()
        when:
        service.process(sampleMessages)
        then:
        2 * mandateProcessRepository.findOneByProcessId(sampleProcessId2) >>
                MandateProcess.builder()
                        .processId(sampleProcessId2)
                        .successful(true)
                        .build()
    }


    List<String> sampleMessages = [
            MandateXmlMessage.builder().id(sampleProcessId1).message("message").type(MandateApplicationType.TRANSFER).build(),
            MandateXmlMessage.builder().id(sampleProcessId2).message("message").type(MandateApplicationType.TRANSFER).build()
    ]

}
