package ee.tuleva.onboarding.mandate.processor

import ee.tuleva.onboarding.epis.EpisService
import ee.tuleva.onboarding.epis.mandate.MandateDto
import ee.tuleva.onboarding.epis.mandate.MandateResponseDTO
import ee.tuleva.onboarding.error.response.ErrorsResponse
import ee.tuleva.onboarding.mandate.Mandate
import ee.tuleva.onboarding.user.User
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleMandate

class MandateProcessorServiceSpec extends Specification {

    MandateProcessRepository mandateProcessRepository = Mock(MandateProcessRepository)
    MandateProcessErrorResolver mandateProcessErrorResolver = Mock(MandateProcessErrorResolver)
    EpisService episService = Mock(EpisService)

    MandateProcessorService service = new MandateProcessorService(
        mandateProcessRepository, mandateProcessErrorResolver, episService)


    User sampleUser = sampleUser().build()
    Mandate sampleMandate = sampleMandate()

    def "Start: starts processing mandate and saves mandate processes"(Integer pillar) {
        given:
        Mandate mandate = sampleMandate()
        mandate.pillar = pillar
        def mandateResponse = new MandateResponseDTO()
        def response = new MandateResponseDTO.MandateResponse()
        mandateResponse.mandateResponses = [response]
        1 * mandateProcessRepository.findOneByProcessId(_) >> new MandateProcess()
        when:
        service.start(sampleUser, mandate)
        then:
        3 * mandateProcessRepository.save({ MandateProcess mandateProcess ->
            mandateProcess.mandate == mandate && mandateProcess.processId != null
        }) >> { args -> args[0] }
        1 * episService.sendMandate({ MandateDto dto ->
            dto.pillar == mandate.pillar
        }) >> mandateResponse
        where:
        pillar | _
        2      | _
        3      | _
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
