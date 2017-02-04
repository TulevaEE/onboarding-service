package ee.tuleva.onboarding.mandate

import com.codeborne.security.mobileid.MobileIdSignatureSession
import ee.tuleva.onboarding.mandate.pdf.PdfService
import ee.tuleva.onboarding.sign.MobileIdSignService
import ee.tuleva.onboarding.user.User
import spock.lang.Specification

class MandateServiceSpec extends Specification {

    def mandateRepository = Mock(MandateRepository)
    def signService = Mock(MobileIdSignService)
    def pdfService = Mock(PdfService)
    def service = new MandateService(mandateRepository, signService, pdfService)

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
        1 * pdfService.toPdf(mandate) >> ([0] as byte[])
        1 * signService.startSign(_, "38501010002", "5555555") >> new MobileIdSignatureSession(1, null, "1234")

        when:
        def session = service.sign(sampleMandateId, sampleUser())

        then:
        session.sessCode == 1
        session.challenge == "1234"
    }

    def "signature status works"() {
        given:
        1 * signService.getSignedFile(_) >> file

        when:
        def status = service.getSignatureStatus(new MandateSignatureSession())

        then:
        status == expectedStatus

        where:
        file          | expectedStatus
        null          | "OUTSTANDING_TRANSACTION"
        [0] as byte[] | "SIGNATURE"
    }

    User sampleUser() {
        return User.builder()
                .personalCode("38501010002")
                .phoneNumber("5555555")
                .build()
    }

    Mandate sampleMandate() {
        return Mandate.builder()
                .fundTransferExchanges(null)
                .futureContributionFundIsin("sample isin")
                .build()
    }

}
