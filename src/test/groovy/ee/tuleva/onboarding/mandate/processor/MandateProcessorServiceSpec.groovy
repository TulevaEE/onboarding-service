package ee.tuleva.onboarding.mandate.processor

import ee.tuleva.onboarding.auth.UserFixture
import ee.tuleva.onboarding.error.response.ErrorsResponse
import ee.tuleva.onboarding.mandate.Mandate
import ee.tuleva.onboarding.mandate.MandateFixture
import ee.tuleva.onboarding.mandate.content.MandateXmlMessage
import ee.tuleva.onboarding.mandate.content.MandateXmlService
import ee.tuleva.onboarding.mandate.processor.implementation.MhubProcessRunner
import ee.tuleva.onboarding.user.User
import spock.lang.Specification

class MandateProcessorServiceSpec extends Specification {

    MandateXmlService mandateXmlService = Mock(MandateXmlService)
    MandateProcessRepository mandateProcessRepository = Mock(MandateProcessRepository)
    MandateProcessErrorResolver mandateProcessErrorResolver = Mock(MandateProcessErrorResolver)
    MhubProcessRunner mhubProcessRunner = Mock(MhubProcessRunner);

    MandateProcessorService service = new MandateProcessorService(mandateXmlService,
            mandateProcessRepository, mandateProcessErrorResolver, mhubProcessRunner)


    User sampleUser = UserFixture.sampleUser()
    Mandate sampleMandate = MandateFixture.sampleMandate()
    List<MandateXmlMessage> sampleMessages = [];

    def "Start: starts processing mandate and saves mandate processes for every mandate message"() {
        given:
        1 * mandateXmlService.getRequestContents(sampleUser, sampleMandate.id) >> sampleMessages
        1 * mhubProcessRunner.process(sampleMessages);
        when:
        service.start(sampleUser, sampleMandate)
        then:
        sampleMessages.size() * mandateProcessRepository.save({ MandateProcess mandateProcess ->
            mandateProcess.mandate == sampleMandate && mandateProcess.processId != null &&
                    mandateProcess.type == sampleMessages.get(0).type
        })
    }

    def "IsFinished: processing is complete when all message processes are finished"() {
        given:
        1 * mandateProcessRepository.findAllByMandate(sampleMandate) >> sampleCompleteProcesses
        when:
        boolean isFinished = service.isFinished(sampleMandate)
        then:
        isFinished == true
    }

    def "IsFinished: processing is not complete when all message processes are not finished"() {
        given:
        1 * mandateProcessRepository.findAllByMandate(sampleMandate) >> sampleIncompleteProcesses
        when:
        boolean isFinished = service.isFinished(sampleMandate)
        then:
        isFinished == false
    }

    def "getErrors: get errors response"() {
        given:
        ErrorsResponse sampleErrorsResponse = new ErrorsResponse([])

        1 * mandateProcessRepository.findAllByMandate(sampleMandate) >> sampleCompleteProcesses
        1 * mandateProcessErrorResolver.getErrors(sampleCompleteProcesses) >> sampleErrorsResponse
        when:
        ErrorsResponse errors = service.getErrors(sampleMandate)
        then:
        errors == sampleErrorsResponse
    }


    List<MandateProcess> sampleCompleteProcesses = [
            MandateProcess.builder().successful(true).build()
    ]

    List<MandateProcess> sampleIncompleteProcesses = [
            MandateProcess.builder().build()
    ]

}
