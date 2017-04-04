package ee.tuleva.onboarding.mandate

import com.codeborne.security.mobileid.IdCardSignatureSession
import com.codeborne.security.mobileid.MobileIdSignatureSession
import com.codeborne.security.mobileid.SignatureFile
import ee.tuleva.onboarding.error.exception.ErrorsResponseException
import ee.tuleva.onboarding.error.response.ErrorsResponse
import ee.tuleva.onboarding.mandate.command.CreateMandateCommand
import ee.tuleva.onboarding.mandate.command.CreateMandateCommandToMandateConverter
import ee.tuleva.onboarding.mandate.content.MandateContentFile
import ee.tuleva.onboarding.mandate.email.EmailService
import ee.tuleva.onboarding.mandate.exception.InvalidMandateException
import ee.tuleva.onboarding.mandate.processor.MandateProcessorService
import ee.tuleva.onboarding.mandate.signature.SignatureService
import ee.tuleva.onboarding.mandate.statistics.FundTransferStatisticsService
import ee.tuleva.onboarding.mandate.statistics.FundValueStatistics
import ee.tuleva.onboarding.mandate.statistics.FundValueStatisticsFixture
import ee.tuleva.onboarding.mandate.statistics.FundValueStatisticsRepository
import ee.tuleva.onboarding.user.User
import ee.tuleva.onboarding.user.UserPreferences
import spock.lang.Specification

import static ee.tuleva.onboarding.mandate.MandateFixture.invalidCreateMandateCommand
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleCreateMandateCommand

class MandateServiceSpec extends Specification {

    MandateRepository mandateRepository = Mock(MandateRepository)
    SignatureService signService = Mock(SignatureService)
    CreateMandateCommandToMandateConverter converter = new CreateMandateCommandToMandateConverter()
    EmailService emailService = Mock(EmailService)
    FundValueStatisticsRepository fundValueStatisticsRepository = Mock(FundValueStatisticsRepository);
    FundTransferStatisticsService fundTransferStatisticsService = Mock(FundTransferStatisticsService);
    MandateProcessorService mandateProcessor = Mock(MandateProcessorService);
    MandateFileService mandateFileService = Mock(MandateFileService)

    MandateService service = new MandateService(mandateRepository, signService,
            converter, emailService, fundValueStatisticsRepository, fundTransferStatisticsService,
            mandateProcessor, mandateFileService)

    Long sampleMandateId = 1L
    UUID sampleStatisticsIdentifier = UUID.randomUUID()
    List<FundValueStatistics> sampleFundValueStatisticsList = FundValueStatisticsFixture.sampleFundValueStatisticsList()
    User sampleUser = sampleUser()


    def "save: Converting create mandate command and persisting a mandate"() {
        given:
            1 * mandateRepository.save(_ as Mandate) >> { Mandate mandate ->
                return mandate
            }
            CreateMandateCommand createMandateCmd = sampleCreateMandateCommand()
        when:
            Mandate mandate = service.save(sampleUser(), createMandateCmd)
        then:
            mandate.futureContributionFundIsin == createMandateCmd.futureContributionFundIsin
            mandate.fundTransferExchanges.size() == createMandateCmd.fundTransferExchanges.size()
            mandate.fundTransferExchanges.first().sourceFundIsin ==
                    createMandateCmd.fundTransferExchanges.first().sourceFundIsin

            mandate.fundTransferExchanges.first().targetFundIsin ==
                    createMandateCmd.fundTransferExchanges.first().targetFundIsin

            mandate.fundTransferExchanges.first().amount ==
                    createMandateCmd.fundTransferExchanges.first().amount

    }

    def "save: Create mandate with invalid CreateMandateCommand fails"() {
        given:
        CreateMandateCommand createMandateCmd = invalidCreateMandateCommand()
        when:
        Mandate mandate = service.save(sampleUser(), createMandateCmd)
        then:
        thrown InvalidMandateException
    }

    def "mobile id signing works"() {
        given:
        def user = sampleUser()
        1 * mandateFileService.getMandateFiles(sampleMandateId, user) >> sampleFiles()
        1 * signService.startSign(_ as List<SignatureFile>, user.getPersonalCode(), user.getPhoneNumber()) >>
                new MobileIdSignatureSession(1, "1234")

        when:
        def session = service.mobileIdSign(sampleMandateId, user, user.getPhoneNumber())

        then:
        session.sessCode == 1
        session.challenge == "1234"
    }

    def "finalizeMobileIdSignature: get correct status if currently signing mandate"() {
        given:
        Mandate sampleMandate = MandateFixture.sampleUnsignedMandate()

        1 * mandateRepository.findByIdAndUser(sampleMandate.id, sampleUser) >> sampleMandate
        1 * signService.getSignedFile(_) >> null

        when:
        def status = service.finalizeMobileIdSignature(sampleUser, sampleStatisticsIdentifier, sampleMandate.id, new MobileIdSignatureSession(0, null))

        then:
        status == "OUTSTANDING_TRANSACTION"
    }

    def "finalizeMobileIdSignature: get correct status if currently signed a mandate and start processing"() {
        given:
        Mandate sampleMandate = MandateFixture.sampleUnsignedMandate()
        byte[] sampleFile = "file".getBytes()

        1 * mandateRepository.findByIdAndUser(sampleMandate.id, sampleUser) >> sampleMandate

        1 * signService.getSignedFile(_) >> sampleFile
        1 * mandateRepository.save({ Mandate it -> it.mandate.get() == sampleFile }) >> sampleMandate

        when:
        def status = service.finalizeMobileIdSignature(sampleUser, sampleStatisticsIdentifier, sampleMandate.id, new MobileIdSignatureSession(0, null))

        then:
        1 * mandateProcessor.start(sampleUser, sampleMandate)
        status == "OUTSTANDING_TRANSACTION"
    }

    def "finalizeMobileIdSignature: get correct status if mandate is signed and being processed"() {
        given:
        Mandate sampleMandate = MandateFixture.sampleMandate()

        1 * mandateRepository.findByIdAndUser(sampleMandate.id, sampleUser) >> sampleMandate
        1 * mandateProcessor.isFinished(sampleMandate) >> false

        when:
        def status = service.finalizeMobileIdSignature(sampleUser, sampleStatisticsIdentifier, sampleMandate.id, new MobileIdSignatureSession(0, null))

        then:
        status == "OUTSTANDING_TRANSACTION"
    }

    def "finalizeMobileIdSignature: get correct status and save statistics if mandate is signed and processed"() {
        given:
        Mandate sampleMandate = MandateFixture.sampleMandate()

        1 * mandateRepository.findByIdAndUser(sampleMandate.id, sampleUser) >> sampleMandate
        1 * mandateProcessor.isFinished(sampleMandate) >> true
        1 * mandateProcessor.getErrors(sampleMandate) >> sampleEmptyErrorsResponse

        when:
        def status = service.finalizeMobileIdSignature(sampleUser, sampleStatisticsIdentifier, sampleMandate.id, new MobileIdSignatureSession(0, null))

        then:
        status == "SIGNATURE"
        1 * fundValueStatisticsRepository.findByIdentifier(sampleStatisticsIdentifier) >> sampleFundValueStatisticsList

    }

    def "finalizeMobileIdSignature: throw exception if mandate is signed and processed and has errors"() {
        given:
        Mandate sampleMandate = MandateFixture.sampleMandate()

        1 * mandateRepository.findByIdAndUser(sampleMandate.id, sampleUser) >> sampleMandate
        1 * mandateProcessor.isFinished(sampleMandate) >> true
        1 * mandateProcessor.getErrors(sampleMandate) >> sampleErrorsResponse

        when:
        def status = service.finalizeMobileIdSignature(sampleUser, sampleStatisticsIdentifier, sampleMandate.id, new MobileIdSignatureSession(0, null))

        then:
        thrown ErrorsResponseException

    }

    def "id card signing works"() {
        given:
        def user = sampleUser()
        1 * mandateFileService.getMandateFiles(sampleMandateId, user) >> sampleFiles()
        1 * signService.startSign(_ as List<SignatureFile>, "signingCertificate") >>
                new IdCardSignatureSession(1, "sigId", "hash")

        when:
        def session = service.idCardSign(sampleMandateId, user, "signingCertificate")

        then:
        session.sessCode == 1
        session.signatureId == "sigId"
        session.hash == "hash"
    }

    def "finalizeIdCardSignature: throws exception when no signed file exist"() {
        given:
        Mandate sampleMandate = MandateFixture.sampleUnsignedMandate()
        IdCardSignatureSession session = new IdCardSignatureSession(1, "sigId", "hash")

        1 * mandateRepository.findByIdAndUser(sampleMandate.id, sampleUser) >> sampleMandate
        1 * signService.getSignedFile(session, "signedHash") >> null

        when:
        def status = service.finalizeIdCardSignature(sampleUser, sampleStatisticsIdentifier, sampleMandate.id, session, "signedHash")

        then:
        thrown(IllegalStateException)
    }

    def "finalizeIdCardSignature: get correct status if currently signed a mandate and start processing"() {
        given:
        Mandate sampleMandate = MandateFixture.sampleUnsignedMandate()
        IdCardSignatureSession session = new IdCardSignatureSession(1, "sigId", "hash")
        byte[] sampleFile = "file".getBytes()
        1 * signService.getSignedFile(session, "signedHash") >> sampleFile
        1 * mandateRepository.findByIdAndUser(sampleMandate.id, sampleUser) >> sampleMandate
        1 * mandateRepository.save({ Mandate it -> it.mandate.get() == sampleFile }) >> sampleMandate

        when:
        def status = service.finalizeIdCardSignature(sampleUser, sampleStatisticsIdentifier, sampleMandate.id, session, "signedHash")

        then:
        1 * mandateProcessor.start(sampleUser, sampleMandate)
        status == "OUTSTANDING_TRANSACTION"
    }

    def "finalizeIdCardSignature: get correct status if mandate is signed and being processed"() {
        given:
        Mandate sampleMandate = MandateFixture.sampleMandate()
        IdCardSignatureSession session = new IdCardSignatureSession(1, "sigId", "hash")

        1 * mandateRepository.findByIdAndUser(sampleMandate.id, sampleUser) >> sampleMandate
        1 * mandateProcessor.isFinished(sampleMandate) >> false

        when:
        def status = service.finalizeIdCardSignature(sampleUser, sampleStatisticsIdentifier, sampleMandate.id, session, "signedHash")

        then:
        status == "OUTSTANDING_TRANSACTION"
    }

    def "finalizeIdCardSignature: get correct status and save statistics if mandate is signed and processed"() {
        given:
        Mandate sampleMandate = MandateFixture.sampleMandate()
        IdCardSignatureSession session = new IdCardSignatureSession(1, "sigId", "hash")

        1 * mandateRepository.findByIdAndUser(sampleMandate.id, sampleUser) >> sampleMandate
        1 * mandateProcessor.isFinished(sampleMandate) >> true
        1 * mandateProcessor.getErrors(sampleMandate) >> sampleEmptyErrorsResponse

        when:
        def status = service.finalizeIdCardSignature(sampleUser, sampleStatisticsIdentifier, sampleMandate.id, session, "signedHash")

        then:
        status == "SIGNATURE"
        1 * fundValueStatisticsRepository.findByIdentifier(sampleStatisticsIdentifier) >> sampleFundValueStatisticsList
    }

    def "finalizeIdCardSignature: throw exception if mandate is signed and processed and has errors"() {
        given:
        Mandate sampleMandate = MandateFixture.sampleMandate()
        IdCardSignatureSession session = new IdCardSignatureSession(1, "sigId", "hash")

        1 * mandateRepository.findByIdAndUser(sampleMandate.id, sampleUser) >> sampleMandate
        1 * mandateProcessor.isFinished(sampleMandate) >> true
        1 * mandateProcessor.getErrors(sampleMandate) >> sampleErrorsResponse

        when:
        def status = service.finalizeIdCardSignature(sampleUser, sampleStatisticsIdentifier, sampleMandate.id, session, "signedHash")

        then:
        thrown ErrorsResponseException

    }

    def mockMandateFilesResponse() {
        1 * mandateContentCreator.
                getContentFiles(_ as User,
                        _ as Mandate,
                        _ as List,
                        _ as UserPreferences) >> sampleFiles()
    }

    User sampleUser() {
        return User.builder()
                .personalCode("38501010002")
                .phoneNumber("5555555")
                .build()
    }

    ErrorsResponse sampleErrorsResponse = new ErrorsResponse([[:]])
    ErrorsResponse sampleEmptyErrorsResponse = new ErrorsResponse([])

    private List<MandateContentFile> sampleFiles() {
        return [new MandateContentFile("file", "html/text", "file".getBytes())]
    }

}
