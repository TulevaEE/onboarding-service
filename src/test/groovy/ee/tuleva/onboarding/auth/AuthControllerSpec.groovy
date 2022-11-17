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
import ee.tuleva.onboarding.auth.smartid.SmartIdSession
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.web.servlet.MockMvc

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post

class AuthControllerSpec extends BaseControllerSpec {

  MobileIdAuthService mobileIdAuthService = Mock(MobileIdAuthService)
  SmartIdAuthService smartIdAuthService = Mock(SmartIdAuthService)
  GenericSessionStore sessionStore = Mock(GenericSessionStore)
  IdCardAuthService idCardAuthService = Mock(IdCardAuthService)
  AuthController controller = new AuthController(mobileIdAuthService, smartIdAuthService, idCardAuthService, sessionStore)
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

  def "Authenticate: throw exception when cert verification not successful"() {
    when:
    MockHttpServletResponse response = mockMvc
        .perform(post("/idLogin")
            .header("ssl-client-verify", "NONE")
            .header("ssl-client-cert", "test_cert")).andReturn().response
    then:
    response.status == HttpStatus.BAD_REQUEST.value()
    0 * idCardAuthService.checkCertificate("test_cert")
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
