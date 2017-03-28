package ee.tuleva.onboarding.mandate

import com.codeborne.security.mobileid.IdCardSignatureSession
import com.codeborne.security.mobileid.MobileIdSignatureSession
import com.codeborne.security.mobileid.SignatureFile
import ee.tuleva.onboarding.fund.Fund
import ee.tuleva.onboarding.fund.FundRepository
import ee.tuleva.onboarding.mandate.command.CreateMandateCommand
import ee.tuleva.onboarding.mandate.command.CreateMandateCommandToMandateConverter
import ee.tuleva.onboarding.mandate.content.MandateContentCreator
import ee.tuleva.onboarding.mandate.content.MandateContentFile
import ee.tuleva.onboarding.mandate.email.EmailService
import ee.tuleva.onboarding.mandate.exception.InvalidMandateException
import ee.tuleva.onboarding.mandate.signature.SignatureService
import ee.tuleva.onboarding.mandate.statistics.FundTransferStatistics
import ee.tuleva.onboarding.mandate.statistics.FundTransferStatisticsRepository
import ee.tuleva.onboarding.user.CsdUserPreferencesService
import ee.tuleva.onboarding.user.User
import ee.tuleva.onboarding.user.UserPreferences
import spock.lang.Specification

import static ee.tuleva.onboarding.auth.UserFixture.sampleUserPreferences
import static ee.tuleva.onboarding.auth.UserFixture.userPreferencesWithAddresPartiallyEmpty
import static ee.tuleva.onboarding.auth.UserFixture.userPreferencesWithContactPreferencesPartiallyEmpty
import static ee.tuleva.onboarding.mandate.MandateFixture.invalidCreateMandateCommand
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleCreateMandateCommand

class MandateServiceSpec extends Specification {

    MandateRepository mandateRepository = Mock(MandateRepository)
    SignatureService signService = Mock(SignatureService)
    MandateContentCreator mandateContentCreator = Mock(MandateContentCreator)
    FundRepository fundRepository = Mock(FundRepository)
    CsdUserPreferencesService csdUserPreferencesService = Mock(CsdUserPreferencesService)
    CreateMandateCommandToMandateConverter converter = new CreateMandateCommandToMandateConverter()
    EmailService emailService = Mock(EmailService)
    FundTransferStatisticsRepository fundTransferExchangeStatisticsRepository = Mock(FundTransferStatisticsRepository)

    MandateService service = new MandateService(mandateRepository, signService, fundRepository,
            mandateContentCreator, csdUserPreferencesService, converter, emailService, fundTransferExchangeStatisticsRepository)

    Long sampleMandateId = 1L

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

    def "getMandateFiles: generates mandate content files"() {
        given:
        User user = sampleUser()
        UserPreferences sampleUserPreferences = sampleUserPreferences()
        mockMandateFiles(user, sampleMandateId, sampleUserPreferences)

        1 * mandateContentCreator.
                getContentFiles(_ as User,
                        _ as Mandate,
                        _ as List,
                        sampleUserPreferences) >> sampleFiles()

        when:
        List<SignatureFile> files = service.getMandateFiles(sampleMandateId, user)

        then:
        files.size() == 1
        files.get(0).mimeType == "html/text"
        files.get(0).content.length == 4
    }

    def "getMandateFiles: on empty user address preferences generates mandate content files with defaults"() {
        given:
        User user = sampleUser()
        UserPreferences sampleUserPreferences = userPreferencesWithAddresPartiallyEmpty()[0]
        mockMandateFiles(user, sampleMandateId, sampleUserPreferences)

        1 * mandateContentCreator.
                getContentFiles(_ as User,
                        _ as Mandate,
                        _ as List,
                        { UserPreferences it ->
                            it.addressRow1 == UserPreferences.defaultUserPreferences().addressRow1
                            it.addressRow2 == UserPreferences.defaultUserPreferences().addressRow2
                            it.addressRow3 == UserPreferences.defaultUserPreferences().addressRow3
                            it.getCountry() == UserPreferences.defaultUserPreferences().getCountry()
                            it.getDistrictCode() == UserPreferences.defaultUserPreferences().getDistrictCode()
                            it.getPostalIndex() == UserPreferences.defaultUserPreferences().getPostalIndex()
                        }) >> sampleFiles()

        when:
        List<SignatureFile> files = service.getMandateFiles(sampleMandateId, user)

        then:
        files.size() == 1
        files.get(0).mimeType == "html/text"
        files.get(0).content.length == 4
    }

    def "getMandateFiles: on empty user contact preferences generates mandate content files with defaults"() {
        given:
        User user = sampleUser()
        UserPreferences sampleUserPreferences = userPreferencesWithContactPreferencesPartiallyEmpty()[0]

        mockMandateFiles(user, sampleMandateId, sampleUserPreferences)

        1 * mandateContentCreator.
                getContentFiles(_ as User,
                        _ as Mandate,
                        _ as List,
                        { UserPreferences it ->
                            it.languagePreference == UserPreferences.defaultUserPreferences().languagePreference
                            it.contactPreference == UserPreferences.defaultUserPreferences().contactPreference
                            it.noticeNeeded == UserPreferences.defaultUserPreferences().noticeNeeded
                        }) >> sampleFiles()

        when:
        List<SignatureFile> files = service.getMandateFiles(sampleMandateId, user)

        then:
        files.size() == 1
        files.get(0).mimeType == "html/text"
        files.get(0).content.length == 4
    }

    def "mobile id signing works"() {
        given:
        def user = sampleUser()
        mockMandateFiles(user, sampleMandateId, sampleUserPreferences())
        mockMandateFilesResponse()
        1 * signService.startSign(_ as List<SignatureFile>, user.getPersonalCode(), user.getPhoneNumber()) >>
                new MobileIdSignatureSession(1, "1234")

        when:
        def session = service.mobileIdSign(sampleMandateId, user, user.getPhoneNumber())

        then:
        session.sessCode == 1
        session.challenge == "1234"
    }

    def "mobile id signature status works"() {
        given:
        1 * signService.getSignedFile(_) >> file
        mandateRepository.findOne(sampleMandateId) >> sampleMandate()
        mandateRepository.save({ Mandate it -> it.mandate == "file".getBytes() }) >> sampleMandate()

        when:
        def status = service.finalizeMobileIdSignature(sampleUser(), sampleMandateId, new MobileIdSignatureSession(0, null))

        then:
        status == expectedStatus

        where:
        file          | expectedStatus
        null          | "OUTSTANDING_TRANSACTION"
        [0] as byte[] | "SIGNATURE"
    }

    def "mobile id signed mandate is saved"() {
        given:
        byte[] file = "file".getBytes()
        User sampleUser = sampleUser()
        1 * signService.getSignedFile(_) >> file
        1 * mandateRepository.findOne(sampleMandateId) >> sampleMandate()
        1 * mandateRepository.save({ Mandate it -> it.mandate == file }) >> sampleMandate()
        1 * emailService.send(sampleUser, sampleMandateId, file)

        when:
        service.finalizeMobileIdSignature(sampleUser, sampleMandateId, new MobileIdSignatureSession(0, null))

        then:
        1 * mandateRepository.findByIdAndUser(sampleMandateId, sampleUser)
        1 * fundTransferExchangeStatisticsRepository.save(_ as FundTransferStatistics)
        true
    }

    def "id card signing works"() {
        given:
        def user = sampleUser()
        mockMandateFiles(user, sampleMandateId, sampleUserPreferences())
        mockMandateFilesResponse()

        signService.startSign(_ as List<SignatureFile>, "signingCertificate") >>
                new IdCardSignatureSession(1, "sigId", "hash")

        when:
        def session = service.idCardSign(sampleMandateId, user, "signingCertificate")

        then:
        session.sessCode == 1
        session.signatureId == "sigId"
        session.hash == "hash"
    }

    def "id card signed mandate is saved"() {
        given:
        byte[] file = "file".getBytes()
        IdCardSignatureSession session = new IdCardSignatureSession(1, "sigId", "hash")
        User sampleUser = sampleUser()
        1 * signService.getSignedFile(session, "signedHash") >> file
        1 * mandateRepository.findOne(sampleMandateId) >> sampleMandate()
        1 * emailService.send(sampleUser, sampleMandateId, file)

        when:
        service.finalizeIdCardSignature(sampleUser, sampleMandateId, session, "signedHash")

        then:
        1 * mandateRepository.save({ Mandate it -> it.mandate == file })
        1 * mandateRepository.findByIdAndUser(sampleMandateId, sampleUser)
        1 * fundTransferExchangeStatisticsRepository.save(_ as FundTransferStatistics)

    }

    def "id card signature finalization throws exception when no signed file exist"() {
        given:
        IdCardSignatureSession session = new IdCardSignatureSession(1, "sigId", "hash")
        1 * signService.getSignedFile(session, "signedHash") >> null

        when:
        service.finalizeIdCardSignature(sampleUser(), sampleMandateId, session, "signedHash")

        then:
        thrown(IllegalStateException)
    }

    def mockMandateFiles(User user, Long mandateId, UserPreferences userPreferences) {
        1 * mandateRepository.findByIdAndUser(mandateId, user) >> sampleMandate()
        1 * fundRepository.findAll() >> [new Fund(), new Fund()]
        1 * csdUserPreferencesService.getPreferences(user.getPersonalCode()) >> userPreferences
    }

    def mockMandateFilesResponse() {
        1 * mandateContentCreator.
                getContentFiles(_ as User,
                        _ as Mandate,
                        _ as List,
                        _ as UserPreferences) >> sampleFiles()
    }

    List<MandateContentFile> sampleFiles() {
        return [new MandateContentFile("file", "html/text", "file".getBytes())]
    }

    User sampleUser() {
        return User.builder()
                .personalCode("38501010002")
                .phoneNumber("5555555")
                .build()
    }

    Mandate sampleMandate() {
        Mandate.builder().build()
    }

}
