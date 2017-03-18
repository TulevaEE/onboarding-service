package ee.tuleva.onboarding.mandate

import com.codeborne.security.mobileid.MobileIDSession
import com.codeborne.security.mobileid.MobileIdSignatureSession
import ee.tuleva.onboarding.BaseControllerSpec
import ee.tuleva.onboarding.auth.idcard.IdCardSessionStore
import ee.tuleva.onboarding.auth.mobileid.MobileIdSessionStore
import ee.tuleva.onboarding.auth.mobileid.MobileIdSignatureSessionStore
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc

import static org.hamcrest.Matchers.is
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

class MandateControllerSpec extends BaseControllerSpec {

    MandateRepository mandateRepository = Mock(MandateRepository)
    MandateService mandateService = Mock(MandateService)
    MobileIdSessionStore mobileIdSessionStore = Mock(MobileIdSessionStore)
    MobileIdSignatureSessionStore mobileIdSignatureSessionStore = Mock(MobileIdSignatureSessionStore)
    IdCardSessionStore idCardSessionStore = Mock(IdCardSessionStore)

    MandateController controller = new MandateController(mandateRepository, mandateService,
            mobileIdSignatureSessionStore, mobileIdSessionStore, idCardSessionStore)

    MockMvc mvc = mockMvc(controller)

    def "save a mandate"() {
        expect:
        mvc
                .perform(post("/v1/mandates/").content(
                mapper.writeValueAsString(
                        MandateFixture.sampleCreateMandateCommand()
                ))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
//TODO                .andExpect(jsonPath('$.futureContributionFundIsin', is(MandateFixture.sampleMandate().futureContributionFundIsin)))

    }

    def "startSign returns the mobile id challenge code"() {
        when:
        mobileIdSessionStore.get() >> dummyMobileIdSessionWithPhone("555")
        mandateService.mobileIdSign(1L, _, "555") >> new MobileIdSignatureSession(1, "1234")

        then:
        mvc
                .perform(put("/v1/mandates/1/signature")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
                .andExpect(jsonPath('$.mobileIdChallengeCode', is("1234")))
    }

    def "startSign fails when there's no mobile id session"() {
        given:
        mobileIdSessionStore.get() >> Optional.empty()

        when:
        mvc
                .perform(put("/v1/mandates/1/signature"))
                .andReturn()

        then:
        thrown Exception
    }

    def "getSignatureStatus returns the mobile id challenge code"() {
        when:
        def session = new MobileIdSignatureSession(1, "1234")
//        Map<String, Object> sessionAttributes = new HashMap<>()
//        sessionAttributes.put("session", session)
        mandateService.getSignatureStatus(MandateFixture.sampleMandate().id, _) >> "SIGNATURE"

        then:
        mvc
                .perform(get("/v1/mandates/" + MandateFixture.sampleMandate().id + "/signature")
//                .sessionAttrs(sessionAttributes)
                )
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
                .andExpect(jsonPath('$.statusCode', is("SIGNATURE")))
    }

    def "getMandateFile returns mandate file"() {
        when:
        1 * mandateRepository
                .findByIdAndUser(MandateFixture.sampleMandate().id, _) >> MandateFixture.sampleMandate()

        then:
        mvc
                .perform(get("/v1/mandates/" + MandateFixture.sampleMandate().id + "/file"))
                .andExpect(status().isOk())
    }

    def "getMandateFile returns not found on non existing mandate file"() {
        when:
        1 * mandateRepository
                .findByIdAndUser(MandateFixture.sampleMandate().id, _) >> null

        then:
        mvc
                .perform(get("/v1/mandates/" + MandateFixture.sampleMandate().id + "/file"))
                .andExpect(status().isNotFound())
    }

    private Optional<MobileIDSession> dummyMobileIdSessionWithPhone(String phone) {
        Optional.of(new MobileIDSession(0, "", "", "", "", phone))
    }

}
