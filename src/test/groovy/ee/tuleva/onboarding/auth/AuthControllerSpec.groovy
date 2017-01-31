package ee.tuleva.onboarding.auth

import com.codeborne.security.mobileid.MobileIDSession
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import spock.lang.Specification

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post

class AuthControllerSpec extends Specification {

    private final static ObjectMapper mapper = new ObjectMapper()

    MobileIdAuthService mobileIdAuthService = Mock(MobileIdAuthService)
    MobileIdSessionStore mobileIdSessionStore = Mock(MobileIdSessionStore)
    AuthController controller = new AuthController(mobileIdAuthService, mobileIdSessionStore)
    private MockMvc mockMvc

    def setup() {
        mockMvc = getMockMvc(controller);
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

    private MockMvc getMockMvc(Object... controllers) {
        MappingJackson2HttpMessageConverter converter = setupJacksonConverter()
        return MockMvcBuilders.standaloneSetup(controllers)
                .setMessageConverters(converter)
                .build()
    }

    private static MappingJackson2HttpMessageConverter setupJacksonConverter() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        converter.setObjectMapper(objectMapper);
        return converter
    }

}
