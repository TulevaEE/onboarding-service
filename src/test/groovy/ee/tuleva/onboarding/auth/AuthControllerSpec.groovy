package ee.tuleva.onboarding.auth

import com.codeborne.security.mobileid.MobileIDSession
import ee.tuleva.onboarding.BaseControllerSpec
import ee.tuleva.onboarding.auth.idcard.IdCardAuthService
import ee.tuleva.onboarding.auth.mobileid.MobileIdAuthService
import ee.tuleva.onboarding.auth.mobileid.MobileIdFixture
import ee.tuleva.onboarding.auth.session.GenericSessionStore
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.web.servlet.MockMvc

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post

class AuthControllerSpec extends BaseControllerSpec {

    MobileIdAuthService mobileIdAuthService = Mock(MobileIdAuthService)
    GenericSessionStore sessionStore = Mock(GenericSessionStore)
    IdCardAuthService idCardAuthService = Mock(IdCardAuthService)
    AuthController controller = new AuthController(mobileIdAuthService, sessionStore, idCardAuthService)
    private MockMvc mockMvc

    def setup() {
        mockMvc = mockMvc(controller)
        controller.idCardSecretToken = "Bearer secretz"
    }

    def "Authenticate: Initiate mobile id authentication"() {
        given:
        1 * mobileIdAuthService.startLogin(MobileIdFixture.samplePhoneNumber) >> MobileIdFixture.sampleMobileIdSession
        1 * sessionStore.save(_ as MobileIDSession)
        when:
        MockHttpServletResponse response = mockMvc
                .perform(post("/authenticate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(sampleAuthenticateCommand()))).andReturn().response
        then:
        response.status == HttpStatus.OK.value()
    }

    def "Authenticate: throw exception when X-Authorization header missing"() {
        when:
        MockHttpServletResponse response = mockMvc
                .perform(post("/idLogin")).andReturn().response
        then:
        response.status == HttpStatus.BAD_REQUEST.value()
        0 * idCardAuthService.checkCertificate(_)
    }

    def "Authenticate: throw exception when invalid X-Authorization header"() {
        when:
        MockHttpServletResponse response = mockMvc
                .perform(post("/idLogin")
                .header("X-Authorization", "Bearer noob")).andReturn().response
        then:
        response.status == HttpStatus.BAD_REQUEST.value()
        0 * idCardAuthService.checkCertificate(_)
    }

    def "Authenticate: throw exception when no cert sent"() {
        when:
        MockHttpServletResponse response = mockMvc
                .perform(post("/idLogin")
                .header("X-Authorization", "Bearer secretz")
                .header("ssl_client_verify", "NONE")).andReturn().response
        then:
        response.status == HttpStatus.BAD_REQUEST.value()
        0 * idCardAuthService.checkCertificate(_)
    }

    def "Authenticate: check successfully verified id card certificate"() {
        when:
        MockHttpServletResponse response = mockMvc
                .perform(post("/idLogin")
                .header("X-Authorization", "Bearer secretz")
                .header("ssl_client_verify", "SUCCESS")
                .header("ssl_client_cert", "test_cert")).andReturn().response
        then:
        response.status == HttpStatus.OK.value()
        1 * idCardAuthService.checkCertificate("test_cert")
    }

    private sampleAuthenticateCommand() {
        [
                phoneNumber: MobileIdFixture.samplePhoneNumber
        ]
    }

}
