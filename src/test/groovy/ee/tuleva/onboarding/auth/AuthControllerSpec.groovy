package ee.tuleva.onboarding.auth

import ee.tuleva.onboarding.BaseControllerSpec
import ee.tuleva.onboarding.auth.command.AuthenticationType
import ee.tuleva.onboarding.auth.mobileid.MobileIDSession
import ee.tuleva.onboarding.auth.mobileid.MobileIdLoginStarter
import ee.tuleva.onboarding.auth.mobileid.MobileIdFixture
import ee.tuleva.onboarding.auth.session.GenericSessionStore
import ee.tuleva.onboarding.auth.webeid.WebEidAuthService
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc

import static ee.tuleva.onboarding.auth.GrantType.PARTNER
import static org.hamcrest.Matchers.is
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

class AuthControllerSpec extends BaseControllerSpec {

  MobileIdLoginStarter mobileIdLoginStarter = Mock(MobileIdLoginStarter)
  WebEidAuthService webEidAuthService = Mock(WebEidAuthService)
  GenericSessionStore sessionStore = Mock(GenericSessionStore)
  AuthService authService = Mock(AuthService)
  AuthController controller = new AuthController(mobileIdLoginStarter, webEidAuthService, sessionStore, authService)
  private MockMvc mockMvc

  def setup() {
    mockMvc = mockMvc(controller)
  }

  def "Authenticate: Initiate mobile id authentication"() {
    given:
    1 * mobileIdLoginStarter.start(MobileIdFixture.samplePhoneNumber, MobileIdFixture.sampleIdCode, false) >> MobileIdFixture.sampleMobileIdSession
    1 * sessionStore.save(_ as MobileIDSession)
    when:
    def result = mockMvc.perform(post("/authenticate")
        .contentType(MediaType.APPLICATION_JSON)
        .content(mapper.writeValueAsString(sampleMobileIdAuthenticateCommand())))
    then:
    result.andExpect(status().isOk())
  }

  def "Authenticate: Initiate mobile id authentication the person asked this browser to remember"() {
    given:
    1 * mobileIdLoginStarter.start(MobileIdFixture.samplePhoneNumber, MobileIdFixture.sampleIdCode, true) >> MobileIdFixture.sampleMobileIdSession
    1 * sessionStore.save(_ as MobileIDSession)
    when:
    def result = mockMvc.perform(post("/authenticate")
        .contentType(MediaType.APPLICATION_JSON)
        .content(mapper.writeValueAsString(sampleMobileIdAuthenticateCommand() + [rememberMe: true])))
    then:
    result.andExpect(status().isOk())
  }

  def "Authenticate: Initiate mobile id authentication without a phone number"() {
    given:
    1 * mobileIdLoginStarter.start(null, MobileIdFixture.sampleIdCode, false) >> MobileIdFixture.sampleMobileIdSession
    1 * sessionStore.save(_ as MobileIDSession)
    when:
    def result = mockMvc.perform(post("/authenticate")
        .contentType(MediaType.APPLICATION_JSON)
        .content(mapper.writeValueAsString([
            personalCode: MobileIdFixture.sampleIdCode,
            type        : AuthenticationType.MOBILE_ID.toString()
        ])))
    then:
    result.andExpect(status().isOk())
  }

  def "Authenticate: Initiate id card authentication via Web eID"() {
    given:
    def challengeNonce = "dGVzdC1jaGFsbGVuZ2Utbm9uY2U="
    1 * webEidAuthService.generateChallenge() >> challengeNonce
    when:
    def result = mockMvc.perform(post("/authenticate")
        .contentType(MediaType.APPLICATION_JSON)
        .content(mapper.writeValueAsString(sampleIdCardAuthenticateCommand())))
    then:
    result.andExpect(status().isOk())
        .andExpect(jsonPath('$.challengeCode', is(challengeNonce)))
  }

  def "the legacy certificate-based /idLogin endpoint no longer exists"() {
    when:
    def postResult = mockMvc.perform(post("/idLogin")
        .header("ssl-client-verify", "SUCCESS")
        .header("ssl-client-cert", "test_cert"))
    def getResult = mockMvc.perform(get("/idLogin")
        .header("ssl-client-verify", "SUCCESS")
        .header("ssl-client-cert", "test_cert"))
    then:
    postResult.andExpect(status().isNotFound())
    getResult.andExpect(status().isNotFound())
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

  def "Authenticate: return validation error with ValidPersonalCode for invalid personal code"() {
    given:
        def invalidRequest = [
            phoneNumber : MobileIdFixture.samplePhoneNumber,
            personalCode: "invalid",
            type        : AuthenticationType.MOBILE_ID.toString()
        ]

    when:
        def result = mockMvc.perform(post("/authenticate")
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(invalidRequest)))

    then:
        result.andExpect(status().isBadRequest())
            .andExpect(jsonPath('$.errors[0].code', is("ValidPersonalCode")))
            .andExpect(jsonPath('$.errors[0].path', is("personalCode")))
  }

  def "Refresh Access Token: successfully refresh token"() {
    given:
        String validRefreshToken = "validRefreshToken"
        String newAccessToken = "newAccessToken"
        String newRefreshToken = "newRefreshToken"
        AuthenticationTokens refreshedTokens = new AuthenticationTokens(newAccessToken, newRefreshToken)
        authService.refreshToken(validRefreshToken) >> refreshedTokens

    when:
        def result = mockMvc.perform(post("/oauth/refresh-token")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"refresh_token":"${validRefreshToken}"}"""))

    then:
        result
            .andExpect(status().isOk())
            .andExpect(jsonPath('$.access_token', is(newAccessToken)))
            .andExpect(jsonPath('$.refresh_token', is(newRefreshToken)))
  }

  def "Refresh Access Token: handle expired refresh token"() {
    given:
        String expiredRefreshToken = "expiredRefreshToken"
        authService.refreshToken(expiredRefreshToken) >> { throw new ExpiredRefreshJwtException() }

    when:
        def result = mockMvc.perform(post("/oauth/refresh-token")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"refresh_token":"${expiredRefreshToken}"}"""))

    then:
        result.andExpect(status().isForbidden())
  }

  private static sampleMobileIdAuthenticateCommand() {
    [
        phoneNumber : MobileIdFixture.samplePhoneNumber,
        personalCode: MobileIdFixture.sampleIdCode,
        type        : AuthenticationType.MOBILE_ID.toString()
    ]
  }

  private static sampleIdCardAuthenticateCommand() {
    [
        type: AuthenticationType.ID_CARD.toString()
    ]
  }

}
