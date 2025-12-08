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
import ee.tuleva.onboarding.auth.webeid.WebEidAuthService
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc

import static ee.tuleva.onboarding.auth.GrantType.PARTNER
import static org.hamcrest.Matchers.is
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

class AuthControllerSpec extends BaseControllerSpec {

  MobileIdAuthService mobileIdAuthService = Mock(MobileIdAuthService)
  SmartIdAuthService smartIdAuthService = Mock(SmartIdAuthService)
  IdCardAuthService idCardAuthService = Mock(IdCardAuthService)
  WebEidAuthService webEidAuthService = Mock(WebEidAuthService)
  GenericSessionStore sessionStore = Mock(GenericSessionStore)
  AuthService authService = Mock(AuthService)
  AuthController controller = new AuthController(mobileIdAuthService, smartIdAuthService, idCardAuthService, webEidAuthService, sessionStore, authService)
  private MockMvc mockMvc

  def setup() {
    mockMvc = mockMvc(controller)
  }

  def "Authenticate: Initiate mobile id authentication"() {
    given:
    1 * mobileIdAuthService.startLogin(MobileIdFixture.samplePhoneNumber, MobileIdFixture.sampleIdCode) >> MobileIdFixture.sampleMobileIdSession
    1 * sessionStore.save(_ as MobileIDSession)
    when:
    def result = mockMvc.perform(post("/authenticate")
        .contentType(MediaType.APPLICATION_JSON)
        .content(mapper.writeValueAsString(sampleMobileIdAuthenticateCommand())))
    then:
    result.andExpect(status().isOk())
  }

  def "Authenticate: Initiate smart id authentication"() {
    given:
    1 * smartIdAuthService.startLogin(SmartIdFixture.personalCode) >> SmartIdFixture.sampleSmartIdSession
    when:
    def result = mockMvc.perform(post("/authenticate")
        .contentType(MediaType.APPLICATION_JSON)
        .content(mapper.writeValueAsString(sampleSmartIdAuthenticateCommand())))
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

  def "Authenticate: throw exception when no cert sent"() {
    when:
    def result = mockMvc.perform(post("/idLogin")
        .header("ssl-client-verify", "NONE"))
    then:
    result.andExpect(status().isBadRequest())
    0 * idCardAuthService.checkCertificate(_)
  }

  def "Authenticate: check successfully verified id card certificate (ALB path - already decoded)"() {
    when:
    def result = mockMvc.perform(post("/idLogin")
        .header("ssl-client-verify", "SUCCESS")
        .header("ssl-client-cert", "test_cert"))
    then:
    result.andExpect(status().isOk())
    1 * idCardAuthService.checkCertificate("test_cert")
  }

  def "Authenticate: check id card certificate with percent-encoding (NGINX path)"() {
    given:
    // NGINX sends percent-encoded certificate with %0A (newline) and %20 (space)
    def nginxEncodedCert = "-----BEGIN+CERTIFICATE-----%0Atest%20content%0A-----END+CERTIFICATE-----"
    def expectedDecodedCert = "-----BEGIN CERTIFICATE-----\ntest content\n-----END CERTIFICATE-----"

    when:
    def result = mockMvc.perform(post("/idLogin")
        .header("ssl-client-verify", "SUCCESS")
        .header("ssl-client-cert", nginxEncodedCert))
    then:
    result.andExpect(status().isOk())
    1 * idCardAuthService.checkCertificate(expectedDecodedCert)
  }

  def "Authenticate: redirect successful id card login back to the app when using the GET method"() {
    when:
    def result = mockMvc.perform(get("/idLogin")
        .header("ssl-client-verify", "SUCCESS")
        .header("ssl-client-cert", "test_cert"))
    then:
    result.andExpect(status().isFound())
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
        def result = mockMvc.perform(post("/authenticate")
            .contentType(MediaType.APPLICATION_JSON)
            .content(invalidRequestBody))

    then:
        result.andExpect(status().isBadRequest())
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

  private static sampleSmartIdAuthenticateCommand() {
    [
        personalCode: SmartIdFixture.personalCode,
        type        : AuthenticationType.SMART_ID.toString()
    ]
  }

  private static sampleIdCardAuthenticateCommand() {
    [
        type: AuthenticationType.ID_CARD.toString()
    ]
  }

}
