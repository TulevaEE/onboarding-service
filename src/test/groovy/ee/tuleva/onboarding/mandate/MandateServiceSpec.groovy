package ee.tuleva.onboarding.mandate

import com.codeborne.security.mobileid.MobileIdSignatureSession
import com.codeborne.security.mobileid.SignatureFile
import ee.tuleva.domain.fund.Fund
import ee.tuleva.domain.fund.FundRepository
import ee.tuleva.onboarding.auth.UserFixture
import ee.tuleva.onboarding.mandate.content.MandateContentCreator
import ee.tuleva.onboarding.mandate.content.MandateContentFile
import ee.tuleva.onboarding.sign.MobileIdSignService
import ee.tuleva.onboarding.user.CsdUserPreferencesService
import ee.tuleva.onboarding.user.User
import ee.tuleva.onboarding.user.UserPreferences
import spock.lang.Specification

class MandateServiceSpec extends Specification {

    MandateRepository mandateRepository = Mock(MandateRepository)
    MobileIdSignService signService = Mock(MobileIdSignService)
    MandateContentCreator mandateContentCreator = Mock(MandateContentCreator)
    FundRepository fundRepository = Mock(FundRepository)
    CsdUserPreferencesService csdUserPreferencesService = Mock(CsdUserPreferencesService)
    CreateMandateCommandToMandateConverter converter = new CreateMandateCommandToMandateConverter()

    MandateService service = new MandateService(mandateRepository, signService, fundRepository,
            mandateContentCreator, csdUserPreferencesService, converter)

    Long sampleMandateId = 1L

    def "save: Converting create mandate command and persisting a mandate"() {
        given:
            1 * mandateRepository.save(_ as Mandate) >> { Mandate mandate ->
                return mandate
            }
        when:
            Mandate mandate = service.save(sampleUser(), MandateFixture.sampleCreateMandateCommand())
        then:
            mandate.futureContributionFundIsin == MandateFixture.sampleCreateMandateCommand().futureContributionFundIsin
            mandate.fundTransferExchanges.size() == MandateFixture.sampleCreateMandateCommand().fundTransferExchanges.size()
            mandate.fundTransferExchanges
                    .first().sourceFundIsin == MandateFixture.sampleCreateMandateCommand()
                    .fundTransferExchanges.first().sourceFundIsin

            mandate.fundTransferExchanges
                    .first().targetFundIsin == MandateFixture.sampleCreateMandateCommand()
                    .fundTransferExchanges.first().targetFundIsin

            mandate.fundTransferExchanges
                    .first().amount == MandateFixture.sampleCreateMandateCommand()
                    .fundTransferExchanges.first().amount

    }

    def "signing works"() {
        given:
        def mandate = Mandate.builder().build()
        1 * mandateRepository.findByIdAndUser(sampleMandateId, sampleUser()) >> mandate
        1 * signService.startSign(_ as List<SignatureFile>, sampleUser().getPersonalCode(), sampleUser().getPhoneNumber()) >>
                new MobileIdSignatureSession(1, "1234")
        1 * fundRepository.findAll() >> [new Fund(), new Fund()]
        1 * csdUserPreferencesService.getPreferences(sampleUser().getPersonalCode()) >> UserFixture.sampleUserPreferences()

        1 * mandateContentCreator.
                getContentFiles(_ as User,
                        _ as Mandate,
                        _ as List,
                        _ as UserPreferences) >> [new MandateContentFile("file", "html/text", "file".getBytes())]

        when:
        def session = service.mobileIdSign(sampleMandateId, sampleUser(), sampleUser().getPhoneNumber())

        then:
        session.sessCode == 1
        session.challenge == "1234"
    }

    def "signature status works"() {
        given:
        1 * signService.getSignedFile(_) >> file
        mandateRepository.findOne(sampleMandateId) >> MandateFixture.sampleMandate()
        mandateRepository.save({ Mandate it -> it.mandate == "file".getBytes() }) >> MandateFixture.sampleMandate()

        when:
        def status = service.getSignatureStatus(sampleMandateId, new MobileIdSignatureSession(0, null))

        then:
        status == expectedStatus

        where:
        file          | expectedStatus
        null          | "OUTSTANDING_TRANSACTION"
        [0] as byte[] | "SIGNATURE"
    }

    def "signed mandate is saved"() {
        given:
        byte[] file = "file".getBytes()
        1 * signService.getSignedFile(_) >> file
        1 * mandateRepository.findOne(sampleMandateId) >> MandateFixture.sampleMandate()
        1 * mandateRepository.save({ Mandate it -> it.mandate == file }) >> MandateFixture.sampleMandate()

        when:
        service.getSignatureStatus(sampleMandateId, new MobileIdSignatureSession(0, null))

        then:
        true
    }

    User sampleUser() {
        return User.builder()
                .personalCode("38501010002")
                .phoneNumber("5555555")
                .build()
    }

}
