package ee.tuleva.onboarding.auth;

import static ee.tuleva.onboarding.aml.AmlCheckType.SK_NAME;
import static ee.tuleva.onboarding.auth.browser.ThisBrowser.COOKIE_NAME;
import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.aDeviceLinkSessionResponse;
import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.aSessionSecret;
import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.anAuthenticationIdentity;
import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.completeStatus;
import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.documentNumber;
import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.personalCode;
import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.runningStatus;
import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.sessionSecretDigest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.http.HttpHeaders.SET_COOKIE;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ee.sk.mid.MidAuthentication;
import ee.sk.mid.MidAuthenticationIdentity;
import ee.sk.mid.MidAuthenticationResponseValidator;
import ee.sk.mid.MidAuthenticationResult;
import ee.sk.mid.MidClient;
import ee.sk.mid.rest.MidConnector;
import ee.sk.mid.rest.MidSessionStatusPoller;
import ee.sk.mid.rest.dao.MidSessionStatus;
import ee.sk.mid.rest.dao.response.MidAuthenticationResponse;
import ee.sk.smartid.AuthenticationIdentity;
import ee.sk.smartid.DeviceLinkAuthenticationResponseValidator;
import ee.sk.smartid.NotificationAuthenticationResponseValidator;
import ee.sk.smartid.SmartIdClient;
import ee.sk.smartid.rest.SmartIdConnector;
import ee.sk.smartid.rest.dao.DeviceLinkAuthenticationSessionRequest;
import ee.sk.smartid.rest.dao.NotificationAuthenticationSessionResponse;
import ee.sk.smartid.rest.dao.SessionStatus;
import ee.tuleva.onboarding.aml.AmlCheck;
import ee.tuleva.onboarding.aml.AmlCheckRepository;
import ee.tuleva.onboarding.auth.principal.PersonImpl;
import ee.tuleva.onboarding.auth.smartid.SmartIdCertificateRevocationCheck;
import ee.tuleva.onboarding.auth.smartid.SmartIdCertificateRevokedException;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserRepository;
import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "mock"})
@Import(LoginIntegrationTest.SmartIdTestConfig.class)
@Transactional
class LoginIntegrationTest {

  private static final String SESSION_ID = "test-session-id";
  private static final String PUSH_SESSION_ID = "push-session-id";
  private static final String MOBILE_ID_SESSION_ID = "mobile-id-session-id";

  @Autowired private MockMvc mockMvc;
  @Autowired private JsonMapper objectMapper;
  @Autowired private UserRepository userRepository;
  @Autowired private AmlCheckRepository amlCheckRepository;

  @MockitoBean private SmartIdConnector smartIdConnector;
  @MockitoBean private DeviceLinkAuthenticationResponseValidator deviceLinkResponseValidator;
  @MockitoBean private NotificationAuthenticationResponseValidator notificationResponseValidator;
  @MockitoBean private SmartIdCertificateRevocationCheck certificateRevocationCheck;
  @MockitoBean private MidClient midClient;
  @MockitoBean private MidConnector midConnector;
  @MockitoBean private MidSessionStatusPoller midSessionStatusPoller;
  @MockitoBean private MidAuthenticationResponseValidator midResponseValidator;
  @Autowired private JdbcClient jdbcClient;

  @TestConfiguration
  static class SmartIdTestConfig {
    @Bean
    @Primary
    SmartIdClient testSmartIdClient(SmartIdConnector connector) {
      var client = new SmartIdClient();
      client.setSmartIdConnector(connector);
      client.setRelyingPartyUUID("00000000-0000-4000-8000-000000000000");
      client.setRelyingPartyName("DEMO");
      return client;
    }
  }

  @Test
  void qrCodeLoginCompletesEndToEndAndRemembersTheAccount() throws Exception {
    given(smartIdConnector.initAnonymousDeviceLinkAuthentication(any()))
        .willReturn(aDeviceLinkSessionResponse(SESSION_ID));

    MvcResult start =
        mockMvc
            .perform(
                post("/v1/smart-id/login")
                    .contentType(APPLICATION_JSON)
                    .content("{\"flow\":\"DEVICE_LINK\",\"language\":\"et\"}"))
            .andExpect(status().isOk())
            .andExpect(cookie().exists("SESSION"))
            .andExpect(jsonPath("$.flow").value("DEVICE_LINK"))
            .andExpect(jsonPath("$.web2AppLink", containsString("deviceLinkType=Web2App")))
            .andExpect(jsonPath("$.web2AppLink", containsString("lang=est")))
            .andReturn();
    Cookie session = sessionCookie(start);

    mockMvc
        .perform(get("/v1/smart-id/login/qr-code").cookie(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deviceLink", containsString("deviceLinkType=QR")))
        .andExpect(jsonPath("$.deviceLink", containsString("elapsedSeconds=")));

    given(smartIdConnector.getSessionStatus(SESSION_ID)).willReturn(runningStatus());
    mockMvc
        .perform(post("/oauth/token").cookie(session).param("grant_type", "SMART_ID"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.error").value("AUTHENTICATION_NOT_COMPLETE"));

    SessionStatus status = completeStatus("QR");
    given(smartIdConnector.getSessionStatus(SESSION_ID)).willReturn(status);
    given(deviceLinkResponseValidator.validate(eq(status), any(), isNull(), eq("smart-id-demo")))
        .willReturn(anAuthenticationIdentity());

    MvcResult granted =
        mockMvc
            .perform(post("/oauth/token").cookie(session).param("grant_type", "SMART_ID"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.access_token").isNotEmpty())
            .andExpect(
                header().stringValues(SET_COOKIE, hasItem(containsString(COOKIE_NAME + "="))))
            .andReturn();

    var claims = claimsOf(accessToken(granted));
    assertThat(claims.get("attributes").get("smartIdDocumentNumber").asText())
        .isEqualTo(documentNumber);
    assertThat(claims.get("attributes").get("grantType").asText()).isEqualTo("SMART_ID");
  }

  @Test
  void aSessionCookieHeldBeforeTheLoginStartedCannotRedeemIt() throws Exception {
    given(smartIdConnector.initAnonymousDeviceLinkAuthentication(any()))
        .willReturn(aDeviceLinkSessionResponse(SESSION_ID));
    Cookie planted = sessionCookie(startDeviceLinkLogin());

    MvcResult victimStart =
        mockMvc
            .perform(
                post("/v1/smart-id/login")
                    .cookie(planted)
                    .contentType(APPLICATION_JSON)
                    .content("{\"flow\":\"DEVICE_LINK\",\"language\":\"et\"}"))
            .andExpect(status().isOk())
            .andReturn();
    Cookie victim = sessionCookie(victimStart);
    assertThat(victim.getValue()).isNotEqualTo(planted.getValue());

    SessionStatus status = completeStatus("QR");
    given(smartIdConnector.getSessionStatus(SESSION_ID)).willReturn(status);
    given(deviceLinkResponseValidator.validate(eq(status), any(), isNull(), eq("smart-id-demo")))
        .willReturn(anAuthenticationIdentity());

    mockMvc
        .perform(post("/oauth/token").cookie(planted).param("grant_type", "SMART_ID"))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(post("/oauth/token").cookie(victim).param("grant_type", "SMART_ID"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.access_token").isNotEmpty());
  }

  @Test
  void aLoginWhoseCertificateTheIssuerNoLongerCallsGoodIsRefused() throws Exception {
    given(smartIdConnector.initAnonymousDeviceLinkAuthentication(any()))
        .willReturn(aDeviceLinkSessionResponse(SESSION_ID));
    Cookie session = sessionCookie(startDeviceLinkLogin());
    SessionStatus status = completeStatus("QR");
    AuthenticationIdentity identity = anAuthenticationIdentity();
    given(smartIdConnector.getSessionStatus(SESSION_ID)).willReturn(status);
    given(deviceLinkResponseValidator.validate(eq(status), any(), isNull(), eq("smart-id-demo")))
        .willReturn(identity);
    willThrow(new SmartIdCertificateRevokedException("REVOKED"))
        .given(certificateRevocationCheck)
        .requireNotRevoked(identity.getAuthCertificate());

    mockMvc
        .perform(post("/oauth/token").cookie(session).param("grant_type", "SMART_ID"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("smart.id.certificate.revoked"))
        .andExpect(jsonPath("$.access_token").doesNotExist());
  }

  @Test
  void aCompletedLoginIsRedeemedForTokensOnlyOnce() throws Exception {
    given(smartIdConnector.initAnonymousDeviceLinkAuthentication(any()))
        .willReturn(aDeviceLinkSessionResponse(SESSION_ID));
    Cookie session = sessionCookie(startDeviceLinkLogin());
    SessionStatus status = completeStatus("QR");
    given(smartIdConnector.getSessionStatus(SESSION_ID)).willReturn(status);
    given(deviceLinkResponseValidator.validate(eq(status), any(), isNull(), eq("smart-id-demo")))
        .willReturn(anAuthenticationIdentity());

    mockMvc
        .perform(post("/oauth/token").cookie(session).param("grant_type", "SMART_ID"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.access_token").isNotEmpty());
    mockMvc
        .perform(post("/oauth/token").cookie(session).param("grant_type", "SMART_ID"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.access_token").doesNotExist());
  }

  @Test
  void sameDeviceLoginIsGrantedOnlyAfterTheCallbackArrives() throws Exception {
    var request = new AtomicReference<DeviceLinkAuthenticationSessionRequest>();
    given(smartIdConnector.initAnonymousDeviceLinkAuthentication(any()))
        .willAnswer(
            invocation -> {
              request.set(invocation.getArgument(0));
              return aDeviceLinkSessionResponse(SESSION_ID);
            });
    Cookie session = sessionCookie(startDeviceLinkLogin());

    SessionStatus status = completeStatus("Web2App");
    given(smartIdConnector.getSessionStatus(SESSION_ID)).willReturn(status);
    mockMvc
        .perform(post("/oauth/token").cookie(session).param("grant_type", "SMART_ID"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.error").value("AUTHENTICATION_NOT_COMPLETE"));

    String callbackToken = request.get().initialCallbackUrl().split("\\?value=")[1];
    mockMvc
        .perform(
            post("/v1/smart-id/login/callback")
                .cookie(session)
                .contentType(APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "value",
                            callbackToken,
                            "sessionSecretDigest",
                            sessionSecretDigest(aSessionSecret),
                            "userChallengeVerifier",
                            "verifier"))))
        .andExpect(status().isNoContent());

    given(
            deviceLinkResponseValidator.validate(
                argThat(cached -> "Web2App".equals(cached.getSignature().getFlowType())),
                any(),
                eq("verifier"),
                eq("smart-id-demo")))
        .willReturn(anAuthenticationIdentity());
    mockMvc
        .perform(post("/oauth/token").cookie(session).param("grant_type", "SMART_ID"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.access_token").isNotEmpty());
  }

  @Test
  void callbackWithAWrongTokenIsRejected() throws Exception {
    given(smartIdConnector.initAnonymousDeviceLinkAuthentication(any()))
        .willReturn(aDeviceLinkSessionResponse(SESSION_ID));
    Cookie session = sessionCookie(startDeviceLinkLogin());

    mockMvc
        .perform(
            post("/v1/smart-id/login/callback")
                .cookie(session)
                .contentType(APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "value", "wrong",
                            "sessionSecretDigest", sessionSecretDigest(aSessionSecret),
                            "userChallengeVerifier", "verifier"))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.errors[0].code").value("smart.id.callback.invalid"));
  }

  @Test
  void rememberedAccountLogsInWithAPushNotification() throws Exception {
    Cookie remembered = rememberedAccountCookie(completeQrLogin(anAuthenticationIdentity()));

    mockMvc
        .perform(get("/v1/smart-id/login/remembered-account").cookie(remembered))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.firstName").value("Aadu"))
        .andExpect(jsonPath("$.lastName").value("Kadakas"));

    given(smartIdConnector.initNotificationAuthentication(any(), eq(documentNumber)))
        .willReturn(new NotificationAuthenticationSessionResponse(PUSH_SESSION_ID));
    MvcResult start =
        mockMvc
            .perform(
                post("/v1/smart-id/login")
                    .cookie(remembered)
                    .contentType(APPLICATION_JSON)
                    .content("{\"flow\":\"NOTIFICATION\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.flow").value("NOTIFICATION"))
            .andExpect(jsonPath("$.verificationCode", matchesPattern("\\d{4}")))
            .andReturn();
    Cookie session = sessionCookie(start);

    SessionStatus status = completeStatus("Notification");
    given(smartIdConnector.getSessionStatus(PUSH_SESSION_ID)).willReturn(status);
    given(notificationResponseValidator.validate(eq(status), any(), eq("smart-id-demo")))
        .willReturn(anAuthenticationIdentity());

    mockMvc
        .perform(post("/oauth/token").cookie(session).param("grant_type", "SMART_ID"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.access_token").isNotEmpty());
  }

  @Test
  void aSecondPushLoginFromTheSameBrowserWithinThirtySecondsIsRefused() throws Exception {
    Cookie remembered = rememberedAccountCookie(completeQrLogin(anAuthenticationIdentity()));
    given(smartIdConnector.initNotificationAuthentication(any(), eq(documentNumber)))
        .willReturn(new NotificationAuthenticationSessionResponse(PUSH_SESSION_ID));

    mockMvc
        .perform(
            post("/v1/smart-id/login")
                .cookie(remembered)
                .contentType(APPLICATION_JSON)
                .content("{\"flow\":\"NOTIFICATION\"}"))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/v1/smart-id/login")
                .cookie(remembered)
                .contentType(APPLICATION_JSON)
                .content("{\"flow\":\"NOTIFICATION\"}"))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.errors[0].code").value("auth.too.many.requests"));

    verify(smartIdConnector, times(1)).initNotificationAuthentication(any(), eq(documentNumber));
  }

  @Test
  void aPushLoginWithoutARememberedAccountIsRefused() throws Exception {
    mockMvc
        .perform(
            post("/v1/smart-id/login")
                .contentType(APPLICATION_JSON)
                .content("{\"flow\":\"NOTIFICATION\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void smartIdLoginUpdatesChangedNameFromAuthProvider() throws Exception {
    userRepository.save(
        User.builder()
            .firstName("Aadu")
            .lastName("Kadakas")
            .personalCode(personalCode)
            .active(true)
            .build());
    amlCheckRepository.save(
        AmlCheck.builder().personalCode(personalCode).type(SK_NAME).success(true).build());

    MvcResult granted = completeQrLogin(anAuthenticationIdentity("AADU", "KUUSK-ÕUNAPUU"));

    User user = userRepository.findByPersonalCode(personalCode).orElseThrow();
    assertThat(user.getFirstName()).isEqualTo("Aadu");
    assertThat(user.getLastName()).isEqualTo("Kuusk-Õunapuu");

    var claims = claimsOf(accessToken(granted));
    assertThat(claims.get("firstName").asText()).isEqualTo("Aadu");
    assertThat(claims.get("lastName").asText()).isEqualTo("Kuusk-Õunapuu");

    assertThat(
            amlCheckRepository.findAllByPersonalCodeAndTypeAndSuccess(personalCode, SK_NAME, false))
        .isEmpty();
    assertThat(
            amlCheckRepository.findAllByPersonalCodeAndTypeAndSuccess(personalCode, SK_NAME, true))
        .hasSize(1);
  }

  @Test
  void smartIdLoginWithChangedNameRecordsFailedSkNameCheckAgainstTheStoredName() throws Exception {
    userRepository.save(
        User.builder()
            .firstName("Aadu")
            .lastName("Kadakas")
            .personalCode(personalCode)
            .active(true)
            .build());

    completeQrLogin(anAuthenticationIdentity("AADU", "KUUSK-ÕUNAPUU"));

    List<AmlCheck> failedSkNameChecks =
        amlCheckRepository.findAllByPersonalCodeAndTypeAndSuccess(personalCode, SK_NAME, false);
    assertThat(failedSkNameChecks).hasSize(1);
    assertThat(failedSkNameChecks.getFirst().getMetadata())
        .isEqualTo(
            Map.of(
                "user", new PersonImpl(personalCode, "Aadu", "Kadakas"),
                "person", new PersonImpl(personalCode, "Aadu", "Kuusk-Õunapuu")));
    assertThat(
            amlCheckRepository.findAllByPersonalCodeAndTypeAndSuccess(personalCode, SK_NAME, true))
        .isEmpty();
  }

  @Test
  void aSuccessfulMobileIdLoginRemembersTheCanonicalPhoneOnThisBrowser() throws Exception {
    MvcResult granted = completeMobileIdLogin("+372 5555 5555");

    assertThat(granted.getResponse().getHeaders(SET_COOKIE))
        .anyMatch(value -> value.startsWith(COOKIE_NAME + "="));
    assertThat(rememberedPhonesOf(personalCode)).containsExactly("+37255555555");
  }

  @Test
  void aMobileIdLoginOnABrowserThatRemembersASmartIdAccountKeepsIt() throws Exception {
    Cookie smartIdRemembered = rememberedAccountCookie(completeQrLogin(anAuthenticationIdentity()));

    Cookie bothRemembered =
        rememberedAccountCookie(completeMobileIdLogin("55555555", smartIdRemembered));

    mockMvc
        .perform(get("/v1/smart-id/login/remembered-account").cookie(bothRemembered))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.firstName").value("Aadu"));
    assertThat(rememberedPhonesOf(personalCode)).containsExactly("+37255555555");
  }

  @Test
  void forgettingTheSmartIdAccountKeepsThePhoneMobileIdRemembers() throws Exception {
    Cookie smartIdRemembered = rememberedAccountCookie(completeQrLogin(anAuthenticationIdentity()));
    Cookie bothRemembered =
        rememberedAccountCookie(completeMobileIdLogin("55555555", smartIdRemembered));

    mockMvc
        .perform(delete("/v1/smart-id/login/remembered-account").cookie(bothRemembered))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get("/v1/smart-id/login/remembered-account").cookie(bothRemembered))
        .andExpect(status().isNoContent());
    assertThat(rememberedPhonesOf(personalCode)).containsExactly("+37255555555");
  }

  private MvcResult completeMobileIdLogin(String typedPhoneNumber, Cookie... browserCookies)
      throws Exception {
    given(midConnector.authenticate(any()))
        .willReturn(new MidAuthenticationResponse(MOBILE_ID_SESSION_ID));
    MvcResult start =
        mockMvc
            .perform(
                withCookies(post("/authenticate"), browserCookies)
                    .contentType(APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of(
                                "type", "MOBILE_ID",
                                "phoneNumber", typedPhoneNumber,
                                "personalCode", personalCode))))
            .andExpect(status().isOk())
            .andReturn();
    Cookie session = sessionCookie(start);

    var complete = new MidSessionStatus();
    complete.setState("COMPLETE");
    given(midSessionStatusPoller.fetchFinalAuthenticationSessionStatus(MOBILE_ID_SESSION_ID))
        .willReturn(complete);
    given(midClient.createMobileIdAuthentication(any(), any()))
        .willReturn(
            MidAuthentication.newBuilder()
                .withResult("OK")
                .withSignatureValueInBase64("bGVhc3VyZS4=")
                .build());
    var identity = new MidAuthenticationIdentity();
    identity.setGivenName("Aadu");
    identity.setSurName("Kadakas");
    identity.setIdentityCode(personalCode);
    var result = new MidAuthenticationResult();
    result.setAuthenticationIdentity(identity);
    result.setValid(true);
    given(midResponseValidator.validate(any())).willReturn(result);

    Cookie[] tokenRequestCookies = Arrays.copyOf(browserCookies, browserCookies.length + 1);
    tokenRequestCookies[browserCookies.length] = session;
    return mockMvc
        .perform(post("/oauth/token").cookie(tokenRequestCookies).param("grant_type", "MOBILE_ID"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.access_token").isNotEmpty())
        .andReturn();
  }

  private static MockHttpServletRequestBuilder withCookies(
      MockHttpServletRequestBuilder request, Cookie... cookies) {
    return cookies.length == 0 ? request : request.cookie(cookies);
  }

  private List<String> rememberedPhonesOf(String personalCode) {
    return jdbcClient
        .sql("SELECT phone_number FROM remembered_mobile_id_phone WHERE personal_code = :code")
        .param("code", personalCode)
        .query(String.class)
        .list();
  }

  private MvcResult completeQrLogin(AuthenticationIdentity identity) throws Exception {
    given(smartIdConnector.initAnonymousDeviceLinkAuthentication(any()))
        .willReturn(aDeviceLinkSessionResponse(SESSION_ID));
    Cookie session = sessionCookie(startDeviceLinkLogin());

    SessionStatus status = completeStatus("QR");
    given(smartIdConnector.getSessionStatus(SESSION_ID)).willReturn(status);
    given(deviceLinkResponseValidator.validate(eq(status), any(), isNull(), eq("smart-id-demo")))
        .willReturn(identity);

    return mockMvc
        .perform(post("/oauth/token").cookie(session).param("grant_type", "SMART_ID"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.access_token").isNotEmpty())
        .andReturn();
  }

  private MvcResult startDeviceLinkLogin() throws Exception {
    return mockMvc
        .perform(
            post("/v1/smart-id/login")
                .contentType(APPLICATION_JSON)
                .content("{\"flow\":\"DEVICE_LINK\",\"language\":\"et\"}"))
        .andExpect(status().isOk())
        .andReturn();
  }

  private static Cookie sessionCookie(MvcResult result) {
    Cookie cookie = result.getResponse().getCookie("SESSION");
    assertThat(cookie).isNotNull();
    return cookie;
  }

  private static Cookie rememberedAccountCookie(MvcResult result) {
    String header =
        result.getResponse().getHeaders(SET_COOKIE).stream()
            .filter(value -> value.startsWith(COOKIE_NAME + "="))
            .findFirst()
            .orElseThrow();
    return new Cookie(COOKIE_NAME, header.substring(COOKIE_NAME.length() + 1, header.indexOf(';')));
  }

  private String accessToken(MvcResult result) throws Exception {
    return objectMapper
        .readTree(result.getResponse().getContentAsString())
        .get("access_token")
        .asText();
  }

  private tools.jackson.databind.JsonNode claimsOf(String accessToken) {
    return objectMapper.readTree(Base64.getUrlDecoder().decode(accessToken.split("\\.")[1]));
  }
}
