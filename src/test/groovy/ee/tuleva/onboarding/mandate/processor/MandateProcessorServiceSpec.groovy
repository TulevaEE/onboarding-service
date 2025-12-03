package ee.tuleva.onboarding.mandate.processor

import ee.tuleva.onboarding.epis.EpisService
import ee.tuleva.onboarding.epis.application.ApplicationResponse
import ee.tuleva.onboarding.epis.mandate.ApplicationResponseDTO
import ee.tuleva.onboarding.epis.mandate.MandateDto
import ee.tuleva.onboarding.epis.mandate.command.MandateCommand
import ee.tuleva.onboarding.epis.mandate.command.MandateCommandResponse
import ee.tuleva.onboarding.error.response.ErrorsResponse
import ee.tuleva.onboarding.mandate.Mandate
import ee.tuleva.onboarding.mandate.MandateRepository
import ee.tuleva.onboarding.user.User
import spock.lang.Specification
import spock.lang.Unroll

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser
import static ee.tuleva.onboarding.mandate.MandateFixture.*
import static ee.tuleva.onboarding.mandate.MandateType.*
import static ee.tuleva.onboarding.country.CountryFixture.countryFixture

class MandateProcessorServiceSpec extends Specification {

  MandateProcessRepository mandateProcessRepository = Mock(MandateProcessRepository)
  MandateProcessErrorResolver mandateProcessErrorResolver = Mock(MandateProcessErrorResolver)
  EpisService episService = Mock(EpisService)
  MandateRepository mandateRepository = Mock(MandateRepository)

  MandateProcessorService service = new MandateProcessorService(
      mandateProcessRepository, mandateProcessErrorResolver, episService, mandateRepository)


  User sampleUser = sampleUser().build()
  Mandate sampleMandate = sampleMandate()

  @Unroll
  def "Start: starts processing mandate and saves mandate processes"() {
    given:
    Mandate mandate = sampleMandate()
    mandate.pillar = pillar
    mandate.address = address
    mandate.user = sampleUser
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
    2      | countryFixture().build()
    3      | null
  }

  def "Start: starts processing cancellation mandate and saves mandate processes"() {
    given:
    Mandate mandate = sampleWithdrawalCancellationMandate()
    mandate.address = countryFixture().build()
    mandate.user = sampleUser
    def response = new MandateCommandResponse("1", true, null, null)
    1 * mandateProcessRepository.findOneByProcessId(_) >> new MandateProcess()
    1 * mandateRepository.findById(mandate.id) >> Optional.ofNullable(mandate)
    when:
    service.start(sampleUser, mandate)
    then:
    1 * mandateProcessRepository.save({ MandateProcess mandateProcess ->
      mandateProcess.mandate == mandate && mandateProcess.processId != null
    }) >> { args -> args[0] }
    1 * episService.sendMandateV2({ MandateCommand mandateCommand ->
      mandateCommand.getMandateDto().details.mandateType == WITHDRAWAL_CANCELLATION
    }) >> response
  }

  def "Start: processes mandate with payment rate and saves processes"() {
    given:
    Mandate mandate = sampleMandateWithPaymentRate()
    mandate.address = countryFixture().build()
    mandate.user = sampleUser
    def response = new MandateCommandResponse("1", true, null, null)
    1 * mandateProcessRepository.findOneByProcessId(_) >> new MandateProcess()
    1 * mandateRepository.findById(mandate.id) >> Optional.ofNullable(mandate)
    when:
    service.start(sampleUser, mandate)
    then:
    1 * mandateProcessRepository.save({ MandateProcess mandateProcess ->
      mandateProcess.mandate == mandate && mandateProcess.processId != null
    }) >> { args -> args[0] }
    1 * episService.sendMandateV2({ MandateCommand mandateCommand ->
      mandateCommand.getMandateDto().details.mandateType == PAYMENT_RATE_CHANGE
    }) >> response
  }
//  def "Start: processes mandate with payment rate and saves processes"() {
//    given:
//    Mandate mandate = sampleMandateWithPaymentRate()
//    mandate.address = countryFixture().build()
//    mandate.user = sampleUser
//    def mandateResponse = new ApplicationResponseDTO()
//    def response = new ApplicationResponse()
//    mandateResponse.mandateResponses = [response]
//
//    1 * mandateProcessRepository.findOneByProcessId(_) >> new MandateProcess()
//    1 * episService.sendMandate({ MandateDto dto ->
//      dto.paymentRate.isPresent() && dto.paymentRate.get() == mandate.paymentRate
//    }) >> mandateResponse
//
//    when:
//    service.start(sampleUser, mandate)
//
//    then:
//    1 * mandateProcessRepository.save({ MandateProcess mandateProcess ->
//      mandateProcess.mandate == mandate && mandateProcess.processId != null
//    }) >> { args -> args[0] }
//  }


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
