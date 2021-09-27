package ee.tuleva.onboarding.mandate

import ee.tuleva.onboarding.account.AccountStatementService
import ee.tuleva.onboarding.aml.exception.AmlChecksMissingException
import ee.tuleva.onboarding.conversion.UserConversionService
import ee.tuleva.onboarding.epis.EpisService
import ee.tuleva.onboarding.error.response.ErrorResponse
import ee.tuleva.onboarding.error.response.ErrorsResponse
import ee.tuleva.onboarding.fund.Fund
import ee.tuleva.onboarding.fund.FundRepository
import ee.tuleva.onboarding.mandate.builder.ConversionDecorator
import ee.tuleva.onboarding.mandate.builder.CreateMandateCommandToMandateConverter
import ee.tuleva.onboarding.mandate.command.CreateMandateCommand
import ee.tuleva.onboarding.mandate.content.MandateContentFile
import ee.tuleva.onboarding.mandate.event.AfterMandateSignedEvent
import ee.tuleva.onboarding.mandate.event.BeforeMandateCreatedEvent
import ee.tuleva.onboarding.mandate.exception.InvalidMandateException
import ee.tuleva.onboarding.mandate.processor.MandateProcessorService
import ee.tuleva.onboarding.mandate.signature.SignatureFile
import ee.tuleva.onboarding.mandate.signature.SignatureService
import ee.tuleva.onboarding.mandate.signature.idcard.IdCardSignatureSession
import ee.tuleva.onboarding.mandate.signature.mobileid.MobileIdSignatureSession
import ee.tuleva.onboarding.user.User
import ee.tuleva.onboarding.user.UserService
import org.springframework.context.ApplicationEventPublisher
import spock.lang.Specification

import static ee.tuleva.onboarding.conversion.ConversionResponseFixture.fullyConverted
import static ee.tuleva.onboarding.epis.contact.ContactDetailsFixture.contactDetailsFixture
import static ee.tuleva.onboarding.mandate.MandateFixture.*
import static java.util.Locale.ENGLISH

class MandateServiceSpec extends Specification {

  MandateRepository mandateRepository = Mock()
  SignatureService signService = Mock()
  FundRepository fundRepository = Mock()
  AccountStatementService accountStatementService = Mock()
  CreateMandateCommandToMandateConverter converter = new CreateMandateCommandToMandateConverter(accountStatementService, fundRepository, new ConversionDecorator())
  MandateProcessorService mandateProcessor = Mock()
  MandateFileService mandateFileService = Mock()
  UserService userService = Mock()
  EpisService episService = Mock()
  ApplicationEventPublisher eventPublisher = Mock()
  UserConversionService conversionService = Mock()

  MandateService service = new MandateService(mandateRepository, signService, converter, mandateProcessor,
    mandateFileService, userService, episService, eventPublisher, conversionService)

  Long sampleMandateId = 1L
  User sampleUser = sampleUser()

  def setup() {
    userService.getById(sampleUser.id) >> sampleUser
  }

  def "save: Converting create mandate command and persisting a mandate"() {
    given:
    1 * mandateRepository.save(_ as Mandate) >> { Mandate mandate ->
      return mandate
    }
    CreateMandateCommand createMandateCmd = sampleCreateMandateCommand()
    when:
    Mandate mandate = service.save(sampleUser.id, createMandateCmd)
    then:
    mandate.futureContributionFundIsin == Optional.of(createMandateCmd.futureContributionFundIsin)
    mandate.fundTransferExchanges.size() == createMandateCmd.fundTransferExchanges.size()
    mandate.fundTransferExchanges.first().sourceFundIsin ==
      createMandateCmd.fundTransferExchanges.first().sourceFundIsin

    mandate.fundTransferExchanges.first().targetFundIsin ==
      createMandateCmd.fundTransferExchanges.first().targetFundIsin

    mandate.fundTransferExchanges.first().amount ==
      createMandateCmd.fundTransferExchanges.first().amount
    1 * episService.getContactDetails(sampleUser) >> contactDetailsFixture()
    1 * fundRepository.findByIsin(createMandateCmd.futureContributionFundIsin) >> Fund.builder().pillar(2).build()
    1 * eventPublisher.publishEvent(_ as BeforeMandateCreatedEvent)
    1 * conversionService.getConversion(sampleUser) >> fullyConverted()
  }

  def "save: Create mandate with invalid CreateMandateCommand fails"() {
    given:
    CreateMandateCommand createMandateCmd = invalidCreateMandateCommand()
    when:
    service.save(sampleUser.id, createMandateCmd)
    then:
    InvalidMandateException exception = thrown()
    exception.errorsResponse.errors.first().code == "invalid.mandate.source.amount.exceeded"
  }

  def "save: Create mandate with missing aml checks fails"() {
    given:
    CreateMandateCommand createMandateCmd = sampleCreateMandateCommand()
    when:
    service.save(sampleUser.id, createMandateCmd)
    then:
    AmlChecksMissingException exception = thrown()
    exception.errorsResponse.errors.first().code == "invalid.mandate.checks.missing"
    0 * mandateRepository.save(_)
    1 * eventPublisher.publishEvent(_ as BeforeMandateCreatedEvent) >> { throw AmlChecksMissingException.newInstance() }
    1 * fundRepository.findByIsin(createMandateCmd.futureContributionFundIsin) >> Fund.builder().pillar(2).build()
    1 * conversionService.getConversion(sampleUser) >> fullyConverted()
    1 * episService.getContactDetails(sampleUser) >> contactDetailsFixture()
  }

  def "save: Create mandate with same source and target fund fails"() {
    given:
    CreateMandateCommand createMandateCmd = invalidCreateMandateCommandWithSameSourceAndTargetFund
    when:
    service.save(sampleUser.id, createMandateCmd)
    then:
    InvalidMandateException exception = thrown()
    exception.errorsResponse.errors.first().code == "invalid.mandate.same.source.and.target.transfer.present"
  }

  def "mobile id signing works"() {
    given:
    def user = sampleUser()
    def signatureSession = MobileIdSignatureSession.builder().build()

    1 * mandateFileService.getMandateFiles(sampleMandateId, user.id) >> sampleFiles()
    1 * signService.startMobileIdSign(_ as List<SignatureFile>, user.personalCode, user.phoneNumber) >>
      signatureSession

    when:
    def session = service.mobileIdSign(sampleMandateId, user.id, user.phoneNumber)

    then:
    session == signatureSession
  }

  def "finalizeMobileIdSignature: get correct status if currently signing mandate"() {
    given:
    Mandate sampleMandate = sampleUnsignedMandate()
    def signatureSession = MobileIdSignatureSession.builder().build()

    1 * mandateRepository.findByIdAndUserId(sampleMandate.id, sampleUser.id) >> sampleMandate
    1 * signService.getSignedFile(_) >> null

    when:
    def status = service.finalizeMobileIdSignature(sampleUser.id, sampleMandate.id, signatureSession, ENGLISH)

    then:
    status == "OUTSTANDING_TRANSACTION"
  }

  def "finalizeMobileIdSignature: get correct status if currently signed a mandate and start processing"() {
    given:
    Mandate sampleMandate = sampleUnsignedMandate()
    byte[] sampleFile = "file".getBytes()
    def signatureSession = MobileIdSignatureSession.builder().build()

    1 * mandateRepository.findByIdAndUserId(sampleMandate.id, sampleUser.id) >> sampleMandate
    1 * signService.getSignedFile(_) >> sampleFile
    1 * mandateRepository.save({ Mandate it -> it.mandate.get() == sampleFile }) >> sampleMandate

    when:
    def status = service.finalizeMobileIdSignature(sampleUser.id, sampleMandate.id, signatureSession, ENGLISH)

    then:
    1 * mandateProcessor.start(sampleUser, sampleMandate)
    status == "OUTSTANDING_TRANSACTION"
  }

  def "finalizeMobileIdSignature: get correct status if mandate is signed and being processed"() {
    given:
    Mandate sampleMandate = sampleMandate()
    def signatureSession = MobileIdSignatureSession.builder().build()

    1 * mandateRepository.findByIdAndUserId(sampleMandate.id, sampleUser.id) >> sampleMandate
    1 * mandateProcessor.isFinished(sampleMandate) >> false
    0 * eventPublisher.publishEvent(_)

    when:
    def status = service.finalizeMobileIdSignature(sampleUser.id, sampleMandate.id, signatureSession, ENGLISH)

    then:
    status == "OUTSTANDING_TRANSACTION"
  }

  def "finalizeMobileIdSignature: get correct status and notify and invalidate EPIS cache if mandate is signed and processed"() {
    given:
    Mandate sampleMandate = sampleMandate()
    def signatureSession = MobileIdSignatureSession.builder().build()

    1 * mandateRepository.findByIdAndUserId(sampleMandate.id, sampleUser.id) >> sampleMandate
    1 * mandateProcessor.isFinished(sampleMandate) >> true
    1 * mandateProcessor.getErrors(sampleMandate) >> sampleEmptyErrorsResponse
    1 * episService.clearCache(sampleUser)

    when:
    def status = service.finalizeMobileIdSignature(sampleUser.id, sampleMandate.id, signatureSession, ENGLISH)

    then:
    status == "SIGNATURE"
    1 * eventPublisher.publishEvent({ AfterMandateSignedEvent event ->
      event.user == sampleUser
      event.mandate == sampleMandate
    })
  }

  def "finalizeMobileIdSignature: throw exception if mandate is signed and processed and has errors"() {
    given:
    Mandate sampleMandate = sampleMandate()
    def signatureSession = MobileIdSignatureSession.builder().build()

    1 * mandateRepository.findByIdAndUserId(sampleMandate.id, sampleUser.id) >> sampleMandate
    1 * mandateProcessor.isFinished(sampleMandate) >> true
    1 * mandateProcessor.getErrors(sampleMandate) >> sampleErrorsResponse
    0 * eventPublisher.publishEvent(_)

    when:
    service.finalizeMobileIdSignature(sampleUser.id, sampleMandate.id, signatureSession, ENGLISH)

    then:
    thrown InvalidMandateException

  }

  def "id card signing works"() {
    given:
    def user = sampleUser()
    def signatureSession = IdCardSignatureSession.builder().build()

    1 * mandateFileService.getMandateFiles(sampleMandateId, user.id) >> sampleFiles()
    1 * signService.startIdCardSign(_ as List<SignatureFile>, "signingCertificate") >> signatureSession

    when:
    def session = service.idCardSign(sampleMandateId, user.id, "signingCertificate")

    then:
    session == signatureSession
  }

  def "finalizeIdCardSignature: throws exception when no signed file exist"() {
    given:
    Mandate sampleMandate = sampleUnsignedMandate()
    def signatureSession = IdCardSignatureSession.builder().build()

    1 * mandateRepository.findByIdAndUserId(sampleMandate.id, sampleUser.id) >> sampleMandate
    1 * signService.getSignedFile(signatureSession, "signedHash") >> null
    0 * eventPublisher.publishEvent(_)

    when:
    service.finalizeIdCardSignature(sampleUser.id, sampleMandate.id, signatureSession, "signedHash", ENGLISH)

    then:
    thrown(IllegalStateException)
  }

  def "finalizeIdCardSignature: get correct status if currently signed a mandate and start processing"() {
    given:
    Mandate sampleMandate = sampleUnsignedMandate()
    def signatureSession = IdCardSignatureSession.builder().build()
    byte[] sampleFile = "file".getBytes()
    1 * signService.getSignedFile(signatureSession, "signedHash") >> sampleFile
    1 * mandateRepository.findByIdAndUserId(sampleMandate.id, sampleUser.id) >> sampleMandate
    1 * mandateRepository.save({ Mandate it -> it.mandate.get() == sampleFile }) >> sampleMandate
    0 * eventPublisher.publishEvent(_)

    when:
    def status = service.finalizeIdCardSignature(sampleUser.id, sampleMandate.id, signatureSession, "signedHash", ENGLISH)

    then:
    1 * mandateProcessor.start(sampleUser, sampleMandate)
    status == "OUTSTANDING_TRANSACTION"
  }

  def "finalizeIdCardSignature: get correct status if mandate is signed and being processed"() {
    given:
    Mandate sampleMandate = sampleMandate()
    def signatureSession = IdCardSignatureSession.builder().build()

    1 * mandateRepository.findByIdAndUserId(sampleMandate.id, sampleUser.id) >> sampleMandate
    1 * mandateProcessor.isFinished(sampleMandate) >> false
    0 * eventPublisher.publishEvent(_)

    when:
    def status = service.finalizeIdCardSignature(sampleUser.id, sampleMandate.id, signatureSession, "signedHash", ENGLISH)

    then:
    status == "OUTSTANDING_TRANSACTION"
  }

  def "finalizeIdCardSignature: get correct status and notify and invalidate EPIS cache if mandate is signed and processed"() {
    given:
    Mandate sampleMandate = sampleMandate()
    def signatureSession = IdCardSignatureSession.builder().build()

    1 * mandateRepository.findByIdAndUserId(sampleMandate.id, sampleUser.id) >> sampleMandate
    1 * mandateProcessor.isFinished(sampleMandate) >> true
    1 * mandateProcessor.getErrors(sampleMandate) >> sampleEmptyErrorsResponse
    1 * episService.clearCache(sampleUser)

    when:
    def status = service.finalizeIdCardSignature(sampleUser.id, sampleMandate.id, signatureSession, "signedHash", ENGLISH)

    then:
    status == "SIGNATURE"
    1 * eventPublisher.publishEvent({ AfterMandateSignedEvent event ->
      event.user == sampleUser
      event.mandate == sampleMandate
    })
  }

  def "finalizeIdCardSignature: throw exception if mandate is signed and processed and has errors"() {
    given:
    Mandate sampleMandate = sampleMandate()
    def signatureSession = IdCardSignatureSession.builder().build()

    1 * mandateRepository.findByIdAndUserId(sampleMandate.id, sampleUser.id) >> sampleMandate
    1 * mandateProcessor.isFinished(sampleMandate) >> true
    1 * mandateProcessor.getErrors(sampleMandate) >> sampleErrorsResponse
    0 * eventPublisher.publishEvent(_)

    when:
    service.finalizeIdCardSignature(sampleUser.id, sampleMandate.id, signatureSession, "signedHash", ENGLISH)

    then:
    thrown InvalidMandateException
  }

  User sampleUser() {
    return User.builder()
      .personalCode("38501010002")
      .phoneNumber("5555555")
      .build()
  }

  ErrorsResponse sampleErrorsResponse = new ErrorsResponse([ErrorResponse.builder().code('sampe.error').build()])
  ErrorsResponse sampleEmptyErrorsResponse = new ErrorsResponse([])

  private List<MandateContentFile> sampleFiles() {
    return [new MandateContentFile("file", "html/text", "file".getBytes())]
  }

}
