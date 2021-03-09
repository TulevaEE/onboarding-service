package ee.tuleva.onboarding.mandate.processor

import ee.tuleva.onboarding.epis.EpisService
import ee.tuleva.onboarding.epis.mandate.MandateDto
import ee.tuleva.onboarding.epis.mandate.ApplicationResponseDTO
import ee.tuleva.onboarding.epis.application.ApplicationResponse
import ee.tuleva.onboarding.error.response.ErrorsResponse
import ee.tuleva.onboarding.mandate.Mandate
import ee.tuleva.onboarding.user.User
import spock.lang.Specification
import spock.lang.Unroll

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleMandate
import static ee.tuleva.onboarding.user.address.AddressFixture.addressFixture

class MandateProcessorServiceSpec extends Specification {

    MandateProcessRepository mandateProcessRepository = Mock(MandateProcessRepository)
    MandateProcessErrorResolver mandateProcessErrorResolver = Mock(MandateProcessErrorResolver)
    EpisService episService = Mock(EpisService)

    MandateProcessorService service = new MandateProcessorService(
        mandateProcessRepository, mandateProcessErrorResolver, episService)


    User sampleUser = sampleUser().build()
    Mandate sampleMandate = sampleMandate()

    @Unroll
    def "Start: starts processing mandate and saves mandate processes"() {
        given:
        Mandate mandate = sampleMandate()
        mandate.pillar = pillar
        mandate.address = address
        def mandateResponse = new ApplicationResponseDTO()
        def response = new ApplicationResponse()
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
            dto.address == mandate.address
        }) >> mandateResponse
        where:
        pillar | address
        2      | addressFixture().build()
        3      | null
    }

    def "IsFinished: processing is complete when all message processes are finished"() {
        given:
        1 * mandateProcessRepository.findAllByMandate(sampleMandate) >> sampleCompleteProcesses
        when:
        boolean isFinished = service.isFinished(sampleMandate)
        then:
        isFinished
    }

    def "IsFinished: processing is not complete when all message processes are not finished"() {
        given:
        1 * mandateProcessRepository.findAllByMandate(sampleMandate) >> sampleIncompleteProcesses
        when:
        boolean isFinished = service.isFinished(sampleMandate)
        then:
        !isFinished
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
