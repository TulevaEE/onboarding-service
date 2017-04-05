package ee.tuleva.onboarding.mandate

import com.codeborne.security.mobileid.IdCardSignatureSession
import com.codeborne.security.mobileid.MobileIDSession
import com.codeborne.security.mobileid.MobileIdSignatureSession
import com.codeborne.security.mobileid.SignatureFile
import ee.tuleva.onboarding.BaseControllerSpec
import ee.tuleva.onboarding.auth.session.GenericSessionStore
import ee.tuleva.onboarding.user.User
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult

import static ee.tuleva.onboarding.mandate.MandateFixture.*
import static org.hamcrest.Matchers.is
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

class MandateControllerSpec extends BaseControllerSpec {

    MandateRepository mandateRepository = Mock(MandateRepository)
    MandateService mandateService = Mock(MandateService)
    GenericSessionStore sessionStore = Mock(GenericSessionStore)
    SignatureFileArchiver signatureFileArchiver = Mock(SignatureFileArchiver)
    MandateFileService mandateFileService = Mock(MandateFileService)

    MandateController controller =
            new MandateController(mandateRepository, mandateService, sessionStore,
                    signatureFileArchiver, mandateFileService)

    MockMvc mvc = mockMvc(controller)

    def "save a mandate"() {
        expect:
        mvc
                .perform(post("/v1/mandates/")
                .content(mapper.writeValueAsString(sampleCreateMandateCommand()))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
//TODO                .andExpect(jsonPath('$.futureContributionFundIsin', is(MandateFixture.sampleMandate().futureContributionFundIsin)))

    }

    def "mobile id signature start returns the mobile id challenge code"() {
        when:
        sessionStore.get(MobileIDSession) >> dummyMobileIdSessionWithPhone("555")
        mandateService.mobileIdSign(1L, _, "555") >> new MobileIdSignatureSession(1, "1234")

        then:
        mvc
                .perform(put("/v1/mandates/1/signature/mobileId")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
                .andExpect(jsonPath('$.mobileIdChallengeCode', is("1234")))
    }

    def "mobile id signature start fails when there's no mobile id session"() {
        given:
        sessionStore.get(MobileIDSession) >> Optional.empty()

        when:
        mvc
                .perform(put("/v1/mandates/1/signature/mobileId"))
                .andReturn()

        then:
        thrown Exception
    }

    def "get mobile ID signature status returns the status code"() {
        when:
        UUID statisticsIdentifier = UUID.randomUUID()
        def session = new MobileIdSignatureSession(1, "1234")
        sessionStore.get(MobileIdSignatureSession) >> Optional.of(session)
        mandateService.finalizeMobileIdSignature(_ as User, statisticsIdentifier, 1L, session) >> "SIGNATURE"

        then:
        mvc
                .perform(get("/v1/mandates/1/signature/mobileId/status").header("x-statistics-identifier", statisticsIdentifier))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
                .andExpect(jsonPath('$.statusCode', is("SIGNATURE")))
    }

    def "get mobile ID signature without statistics identifier fails"() {
        expect:
        mvc
                .perform(get("/v1/mandates/1/signature/mobileId/status"))
                .andExpect(status().isBadRequest())
    }

    def "id card signature start returns the hash to be signed by the client"() {
        when:
        mandateService.idCardSign(1L, _, "clientCertificate") >> new IdCardSignatureSession(1, "sigId", "asdfg")

        then:
        mvc
                .perform(put("/v1/mandates/1/signature/idCard")
                .content(mapper.writeValueAsString(sampleStartIdCardSignCommand("clientCertificate")))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
                .andExpect(jsonPath('$.hash', is("asdfg")))
    }

    def "put ID card signature status returns the status code"() {
        when:
        UUID statisticsIdentifier = UUID.randomUUID()
        def session = new IdCardSignatureSession(1, "sigId", "hash")
        sessionStore.get(IdCardSignatureSession) >> Optional.of(session)
        mandateService.finalizeIdCardSignature(_ as User, statisticsIdentifier, 1L, session, "signedHash") >> "SIGNATURE"

        then:
        mvc
                .perform(put("/v1/mandates/1/signature/idCard/status").header("x-statistics-identifier", statisticsIdentifier)
                .content(mapper.writeValueAsString(sampleFinishIdCardSignCommand("signedHash")))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
                .andExpect(jsonPath('$.statusCode', is("SIGNATURE")))
    }

    def "put ID card signature status without statistics identifier fails"() {
        expect:
        mvc
                .perform(put("/v1/mandates/1/signature/idCard/status")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
    }

    def "getMandateFile returns mandate file"() {
        when:
        1 * mandateRepository
                .findByIdAndUser(sampleMandate().id, _) >> sampleMandate()

        then:
        MvcResult result = mvc
                .perform(get("/v1/mandates/" + sampleMandate().id + "/file"))
                .andExpect(status().isOk())
                .andReturn()

        result.getResponse().getHeader("Content-Disposition") == "attachment; filename=Tuleva_avaldus.bdoc"
    }

    def "getMandateFile throws exception if mandate is not signed"() {
        given:
        1 * mandateRepository
                .findByIdAndUser(sampleMandate().id, _) >> sampleUnsignedMandate()

        when:
        mvc
                .perform(get("/v1/mandates/" + sampleMandate().id + "/file"))

        then:
        thrown Exception
    }

    def "getMandateFilePreview: returns mandate preview file"() {
        when:

        List<SignatureFile> files = [new SignatureFile("filename", "text/html", "content".getBytes())]

        1 * mandateFileService.getMandateFiles(sampleMandate().id, _) >> files
        1 * signatureFileArchiver.writeSignatureFilesToZipOutputStream(files, _ as OutputStream)

        then:
        MvcResult result = mvc
                .perform(get("/v1/mandates/" + sampleMandate().id + "/file/preview"))
                .andExpect(status().isOk())
                .andReturn()

        result.getResponse().getHeader("Content-Disposition") == "attachment; filename=Tuleva_avaldus.zip"

    }

    def "getMandateFile returns not found on non existing mandate file"() {
        when:
        1 * mandateRepository
                .findByIdAndUser(sampleMandate().id, _) >> null

        then:
        mvc
                .perform(get("/v1/mandates/" + sampleMandate().id + "/file"))
                .andExpect(status().isNotFound())
    }

    private Optional<MobileIDSession> dummyMobileIdSessionWithPhone(String phone) {
        Optional.of(new MobileIDSession(0, "", "", "", "", phone))
    }

}
