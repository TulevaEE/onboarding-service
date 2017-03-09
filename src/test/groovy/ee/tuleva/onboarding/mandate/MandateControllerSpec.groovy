package ee.tuleva.onboarding.mandate

import com.codeborne.security.mobileid.MobileIDSession
import com.codeborne.security.mobileid.MobileIdSignatureSession
import ee.tuleva.onboarding.BaseControllerSpec
import ee.tuleva.onboarding.auth.mobileid.MobileIdSessionStore
import ee.tuleva.onboarding.auth.mobileid.MobileIdSignatureSessionStore
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import spock.mock.DetachedMockFactory

import static org.hamcrest.Matchers.is
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@WebMvcTest(MandateController)
@WithMockUser
class MandateControllerSpec extends BaseControllerSpec {

    @Autowired
    MandateService mandateService

    @Autowired
    MandateRepository mandateRepository

    @Autowired
    MobileIdSessionStore mobileIdSessionStore

    @Autowired
    MockMvc mvc

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
        mandateService.sign(1L, _, "555") >> new MobileIdSignatureSession(1, null, "1234")

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
        def session = MandateSignatureSession.builder().sessCode(1).challenge("1234").build()
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
                .perform(get("/v1/mandates/" + MandateFixture.sampleMandate().id + "/file")
        )
                .andExpect(status().isOk())
    }

    def "getMandateFile returns not found on non existing mandate file"() {
        when:
        1 * mandateRepository
                .findByIdAndUser(MandateFixture.sampleMandate().id, _) >> null

        then:
        mvc
                .perform(get("/v1/mandates/" + MandateFixture.sampleMandate().id + "/file")
        )
                .andExpect(status().isNotFound())
    }

    private Optional<MobileIDSession> dummyMobileIdSessionWithPhone(String phone) {
        Optional.of(new MobileIDSession(0, "", "", "", "", phone))
    }

    @TestConfiguration
    static class MockConfig {
        def mockFactory = new DetachedMockFactory()

        @Bean
        MandateService mandateService() {
            MandateService mandateService = mockFactory.Mock(MandateService)
//TODO            mandateService.save(_ as User, _ as CreateMandateCommand) >> MandateFixture.sampleMandate()
            return mandateService
        }

        @Bean
        MandateRepository mandateRepository() {
            MandateRepository mandateRepository = mockFactory.Mock(MandateRepository)
            return mandateRepository
        }

        @Bean
        MobileIdSignatureSessionStore mobileIdSignatureSessionStore() {
            return mockFactory.Mock(MobileIdSignatureSessionStore)
        }

        @Bean
        MobileIdSessionStore mobileIdSessionStore() {
            return mockFactory.Mock(MobileIdSessionStore)
        }
    }
}
