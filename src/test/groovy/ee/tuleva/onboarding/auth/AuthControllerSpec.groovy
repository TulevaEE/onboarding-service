package ee.tuleva.onboarding.auth

import ee.tuleva.onboarding.BaseControllerSpec
import ee.tuleva.onboarding.auth.command.AuthenticationType
import ee.tuleva.onboarding.auth.idcard.IdCardAuthService
import ee.tuleva.onboarding.auth.mobileid.MobileIDSession
import ee.tuleva.onboarding.auth.mobileid.MobileIdAuthService
import ee.tuleva.onboarding.auth.mobileid.MobileIdFixture
import ee.tuleva.onboarding.auth.session.GenericSessionStore
import ee.tuleva.onboarding.auth.smartid.SmartIdAuthService
import ee.tuleva.onboarding.auth.smartid.SmartIdFixture
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.web.servlet.MockMvc

import static ee.tuleva.onboarding.auth.GrantType.PARTNER
import static org.hamcrest.Matchers.is
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

class AuthControllerSpec extends BaseControllerSpec {

  MobileIdAuthService mobileIdAuthService = Mock(MobileIdAuthService)
  SmartIdAuthService smartIdAuthService = Mock(SmartIdAuthService)
  GenericSessionStore sessionStore = Mock(GenericSessionStore)
  IdCardAuthService idCardAuthService = Mock(IdCardAuthService)
  AuthService authService = Mock(AuthService)
  AuthController controller = new AuthController(mobileIdAuthService, smartIdAuthService, idCardAuthService, sessionStore, authService)
  private MockMvc mockMvc

  def setup() {
    mockMvc = mockMvc(controller)
  }

  def "Authenticate: Initiate mobile id authentication"() {
    given:
    1 * mobileIdAuthService.startLogin(MobileIdFixture.samplePhoneNumber, MobileIdFixture.sampleIdCode) >> MobileIdFixture.sampleMobileIdSession
    1 * sessionStore.save(_ as MobileIDSession)
    when:
    MockHttpServletResponse response = mockMvc
        .perform(post("/authenticate")
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(sampleMobileIdAuthenticateCommand()))).andReturn().response
    then:
    response.status == HttpStatus.OK.value()
  }

  def "Authenticate: Initiate smart id authentication"() {
    given:
    1 * smartIdAuthService.startLogin(SmartIdFixture.personalCode) >> SmartIdFixture.sampleSmartIdSession
    when:
    MockHttpServletResponse response = mockMvc
        .perform(post("/authenticate")
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(sampleSmartIdAuthenticateCommand()))).andReturn().response
    then:
    response.status == HttpStatus.OK.value()
  }

  def "Authenticate: throw exception when no cert sent"() {
    when:
    MockHttpServletResponse response = mockMvc
        .perform(post("/idLogin")
            .header("ssl-client-verify", "NONE")).andReturn().response
    then:
    response.status == HttpStatus.BAD_REQUEST.value()
    0 * idCardAuthService.checkCertificate(_)
  }

  def "Authenticate: check successfully verified id card certificate"() {
    when:
    MockHttpServletResponse response = mockMvc
        .perform(post("/idLogin")
            .header("ssl-client-verify", "SUCCESS")
            .header("ssl-client-cert", "test_cert")).andReturn().response
    then:
    response.status == HttpStatus.OK.value()
    1 * idCardAuthService.checkCertificate("test_cert")
  }

  def "Authenticate: redirect successful id card login back to the app when using the GET method"() {
    when:
    MockHttpServletResponse response = mockMvc
        .perform(get("/idLogin")
            .header("ssl-client-verify", "SUCCESS")
            .header("ssl-client-cert", "test_cert")).andReturn().response
    then:
    response.status == HttpStatus.FOUND.value()
    1 * idCardAuthService.checkCertificate("test_cert")
  }

  def "POST /v1/tokens with valid handover jwt should return an access token"() {
    given:
    def grantType = PARTNER
    def handoverJwt = "validHandoverJwt"
    def accessToken = "validAccessToken"
    def refreshToken = "refreshToken"
    def tokens = new AuthenticationTokens(accessToken, refreshToken)
    authService.authenticate(PARTNER, handoverJwt) >> tokens

    when:
    def result = mockMvc.perform(post("/v1/tokens")
        .param("grant_type", grantType.name())
        .param("authenticationHash", handoverJwt))

    then:
    result
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath('$.access_token', is(accessToken)))
        .andExpect(jsonPath('$.refresh_token', is(refreshToken)))
  }

  def "Authenticate: return validation error for invalid request"() {
    given:
        String invalidRequestBody = "{}"

    when:
        MockHttpServletResponse response = mockMvc
            .perform(post("/authenticate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidRequestBody)).andReturn().response

    then:
        response.status == HttpStatus.BAD_REQUEST.value()
  }

  def "Refresh Access Token: successfully refresh token"() {
    given:
        String validRefreshToken = "validRefreshToken"
        String newAccessToken = "newAccessToken"
        String newRefreshToken = "newRefreshToken"
        AuthenticationTokens refreshedTokens = new AuthenticationTokens(newAccessToken, newRefreshToken)
        authService.refreshToken(validRefreshToken) >> refreshedTokens

    when:
        MockHttpServletResponse response = mockMvc
            .perform(post("/oauth/refresh-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refresh_token\":\"${validRefreshToken}\"}")).andReturn().response

    then:
        response.status == HttpStatus.OK.value()
        response.contentAsString.contains(newAccessToken)
        response.contentAsString.contains(newRefreshToken)
  }

  def "Refresh Access Token: handle expired refresh token"() {
    given:
        String expiredRefreshToken = "expiredRefreshToken"
        authService.refreshToken(expiredRefreshToken) >> { throw new ExpiredRefreshJwtException() }

    when:
        MockHttpServletResponse response = mockMvc
            .perform(post("/oauth/refresh-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refresh_token\":\"${expiredRefreshToken}\"}")).andReturn().response

    then:
        response.status == HttpStatus.FORBIDDEN.value()
  }

  private static sampleMobileIdAuthenticateCommand() {
    [
        phoneNumber : MobileIdFixture.samplePhoneNumber,
        personalCode: MobileIdFixture.sampleIdCode,
        type        : AuthenticationType.MOBILE_ID.toString()
    ]
  }

  private static sampleSmartIdAuthenticateCommand() {
    [
        personalCode: SmartIdFixture.personalCode,
        type        : AuthenticationType.SMART_ID.toString()
    ]
  }

}
