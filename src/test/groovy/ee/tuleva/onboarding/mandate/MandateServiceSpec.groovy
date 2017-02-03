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

    def "signing works"() {
        given:
        def user = User.builder()
                .personalCode("38501010002")
                .phoneNumber("5555555")
                .build()
        def mandateId = 1L
        def mandate = Mandate.builder().build()
        1 * mandateRepository.findByIdAndUser(mandateId, user) >> mandate
        1 * pdfService.toPdf(mandate) >> ([0] as byte[])
        1 * signService.startSign(_, "38501010002", "5555555") >> new MobileIdSignatureSession(1, null, "1234")

        when:
        def session = service.sign(mandateId, user)

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
}
