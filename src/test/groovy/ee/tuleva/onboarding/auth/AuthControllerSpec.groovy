package ee.tuleva.onboarding.auth

import com.codeborne.security.mobileid.MobileIDSession
import ee.tuleva.onboarding.BaseControllerSpec
import ee.tuleva.onboarding.auth.idcard.IdCardAuthService
import ee.tuleva.onboarding.auth.mobileid.MobileIdAuthService
import ee.tuleva.onboarding.auth.mobileid.MobileIdFixture
import ee.tuleva.onboarding.auth.mobileid.MobileIdSessionStore
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.web.servlet.MockMvc

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post

class AuthControllerSpec extends BaseControllerSpec {

    MobileIdAuthService mobileIdAuthService = Mock(MobileIdAuthService)
    MobileIdSessionStore mobileIdSessionStore = Mock(MobileIdSessionStore)
    IdCardAuthService idCardAuthService = Mock(IdCardAuthService)
    AuthController controller = new AuthController(mobileIdAuthService, mobileIdSessionStore, idCardAuthService)
    private MockMvc mockMvc

    def setup() {
        mockMvc = getMockMvc(controller)
    }

    def "Authenticate: Initiate mobile id authentication"() {
        given:
        1 * mobileIdAuthService.startLogin(MobileIdFixture.samplePhoneNumber) >> MobileIdFixture.sampleMobileIdSession
        1 * mobileIdSessionStore.save(_ as MobileIDSession)
        when:
        MockHttpServletResponse response = mockMvc
                .perform(post("/authenticate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(sampleAuthenticateCommand()))).andReturn().response
        then:
        response.status == HttpStatus.OK.value()

    }

    private sampleAuthenticateCommand() {
        [
                phoneNumber: MobileIdFixture.samplePhoneNumber
        ]
    }

}
