package ee.tuleva.onboarding.auth;

import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.aDeviceLinkSessionResponse;
import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.completeStatus;
import static ee.tuleva.onboarding.event.TrackableEventType.MINOR_SELF_LOGIN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.lessThan;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ee.sk.smartid.AuthenticationIdentity;
import ee.sk.smartid.DeviceLinkAuthenticationResponseValidator;
import ee.sk.smartid.NotificationAuthenticationResponseValidator;
import ee.sk.smartid.SmartIdClient;
import ee.sk.smartid.rest.SmartIdConnector;
import ee.sk.smartid.rest.dao.SessionStatus;
import ee.tuleva.onboarding.auth.ocsp.CertificateRevocationCheck;
import ee.tuleva.onboarding.event.EventLog;
import ee.tuleva.onboarding.event.EventLogRepository;
import ee.tuleva.onboarding.time.ClockHolder;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.Cookie;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "mock"})
@TestPropertySource(properties = "minor-self-service.enabled=true")
@Import(MinorSelfLoginIntegrationTest.SmartIdTestConfig.class)
@Transactional
class MinorSelfLoginIntegrationTest {

  private static final String MINOR = "61506150006";
  private static final Instant WHILE_THE_MINOR_IS_ELEVEN = Instant.parse("2026-10-26T10:00:00Z");
  private static final String SESSION_ID = "minor-session-id";

  @Autowired private MockMvc mockMvc;
  @Autowired private JsonMapper objectMapper;
  @Autowired private EventLogRepository eventLogRepository;
  @Autowired private Clock clock;

  @MockitoBean private SmartIdConnector smartIdConnector;
  @MockitoBean private DeviceLinkAuthenticationResponseValidator deviceLinkResponseValidator;
  @MockitoBean private NotificationAuthenticationResponseValidator notificationResponseValidator;

  @MockitoBean(name = "smartIdCertificateRevocationCheck")
  private CertificateRevocationCheck certificateRevocationCheck;

  private static final KeyPair PARTNER_KEYS = partnerKeys();

  @TestBean(name = "partnerPublicKey1", methodName = "partnerPublicKey")
  private PublicKey partnerPublicKey1;

  @TestBean(name = "partnerPublicKey2", methodName = "partnerPublicKey")
  private PublicKey partnerPublicKey2;

  static PublicKey partnerPublicKey() {
    return PARTNER_KEYS.getPublic();
  }

  private static KeyPair partnerKeys() {
    try {
      var generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      return generator.generateKeyPair();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

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

  @BeforeEach
  void pinTheClockWhileTheMinorIsEleven() {
    ClockHolder.setClock(Clock.fixed(WHILE_THE_MINOR_IS_ELEVEN, ZoneOffset.UTC));
  }

  @AfterEach
  void resetTheClock() {
    ClockHolder.setDefaultClock();
  }

  @Test
  void aMinorLogsInThemselvesWithSmartIdAsAWard() throws Exception {
    MvcResult granted = logInWithSmartIdAs(MINOR);

    JsonNode claims = claimsOf(accessToken(granted));
    assertThat(claims.get("sub").asText()).isEqualTo(MINOR);
    assertThat(claims.get("authorities").values().stream().map(JsonNode::asText))
        .containsExactly("USER", "WARD");
  }

  @Test
  void aMinorsLoginIsTrackedAsAMinorSelfLogin() throws Exception {
    logInWithSmartIdAs(MINOR);

    assertThat(minorSelfLogins()).singleElement();
  }

  @Test
  void everythingTheMinorsAccountPagesLoadAnswers() throws Exception {
    String accessToken = accessToken(logInWithSmartIdAs(MINOR));

    mockMvc
        .perform(authorized(get("/v1/me"), accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.personalCode").value(MINOR))
        .andExpect(jsonPath("$.age").value(lessThan(18)));
    for (String read :
        List.of(
            "/v1/me/conversion",
            "/v1/amlchecks",
            "/v1/pension-account-statement",
            "/v1/funds",
            "/v1/me/roles",
            "/v1/me/pending-onboardings",
            "/v1/me/connected-apps",
            "/v1/savings-fund/gifts",
            "/v1/transactions")) {
      mockMvc.perform(authorized(get(read), accessToken)).andExpect(status().isOk());
    }
    mockMvc
        .perform(authorized(get("/v1/savings-account-statement"), accessToken))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(
            authorized(post("/v1/t"), accessToken)
                .contentType(APPLICATION_JSON)
                .content("{\"type\":\"PAGE_VIEW\",\"data\":{\"path\":\"/account\"}}"))
        .andExpect(status().isOk());
  }

  @Test
  void aMinorWhoLoggedInThemselvesCannotTransact() throws Exception {
    String accessToken = accessToken(logInWithSmartIdAs(MINOR));

    for (MockHttpServletRequestBuilder transaction :
        List.of(
            post("/v1/mandates").contentType(APPLICATION_JSON).content("{}"),
            post("/v1/mandate-batches").contentType(APPLICATION_JSON).content("{}"),
            post("/v1/savings/redemptions").contentType(APPLICATION_JSON).content("{}"),
            patch("/v1/me").contentType(APPLICATION_JSON).content("{}"),
            post("/v1/me/role")
                .contentType(APPLICATION_JSON)
                .content("{\"type\":\"PERSON\",\"code\":\"" + MINOR + "\"}"),
            get("/v1/mandates/1/signature/smart-id/status"),
            get("/v1/payments/link?type=SAVINGS&amount=10&currency=EUR"))) {
      mockMvc
          .perform(authorized(transaction, accessToken))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.error").value("READ_ONLY_ACCESS"));
    }
  }

  @Test
  void aMinorHandedOverByAPartnerIsAWardWhoCannotTransact() throws Exception {
    MvcResult granted =
        mockMvc
            .perform(
                post("/oauth/token")
                    .param("grant_type", "PARTNER")
                    .param("authenticationHash", handoverTokenFor(MINOR)))
            .andExpect(status().isOk())
            .andReturn();
    String accessToken = accessToken(granted);

    assertThat(claimsOf(accessToken).get("authorities").values().stream().map(JsonNode::asText))
        .containsExactly("USER", "WARD");
    mockMvc
        .perform(
            authorized(post("/v1/mandates"), accessToken)
                .contentType(APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("READ_ONLY_ACCESS"));
    assertThat(minorSelfLogins())
        .singleElement()
        .satisfies(
            login ->
                assertThat(String.valueOf(login.getData().get("method"))).isEqualTo("PARTNER"));
  }

  @Test
  void aMinorsRefreshedTokenStaysAWardsToken() throws Exception {
    MvcResult granted = logInWithSmartIdAs(MINOR);

    MvcResult refreshed =
        mockMvc
            .perform(
                post("/oauth/refresh-token")
                    .contentType(APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of("refresh_token", refreshToken(granted)))))
            .andExpect(status().isOk())
            .andReturn();

    String refreshedAccessToken = accessToken(refreshed);
    assertThat(
            claimsOf(refreshedAccessToken).get("authorities").values().stream()
                .map(JsonNode::asText))
        .containsExactly("USER", "WARD");
    mockMvc.perform(authorized(get("/v1/me"), refreshedAccessToken)).andExpect(status().isOk());
  }

  private MvcResult logInWithSmartIdAs(String personalCode) throws Exception {
    given(smartIdConnector.initAnonymousDeviceLinkAuthentication(any()))
        .willReturn(aDeviceLinkSessionResponse(SESSION_ID));
    MvcResult start =
        mockMvc
            .perform(
                post("/v1/smart-id/login")
                    .contentType(APPLICATION_JSON)
                    .content("{\"flow\":\"DEVICE_LINK\",\"language\":\"et\"}"))
            .andExpect(status().isOk())
            .andReturn();
    SessionStatus status = completeStatus("QR");
    given(smartIdConnector.getSessionStatus(SESSION_ID)).willReturn(status);
    given(deviceLinkResponseValidator.validate(eq(status), any(), isNull(), eq("smart-id-demo")))
        .willReturn(identityOf(personalCode));

    return mockMvc
        .perform(
            post("/oauth/token")
                .cookie(sessionCookie(start))
                .param("grant_type", "SMART_ID")
                .param("authenticationHash", authenticationHashOf(start)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.access_token").isNotEmpty())
        .andReturn();
  }

  private List<EventLog> minorSelfLogins() {
    return StreamSupport.stream(eventLogRepository.findAll().spliterator(), false)
        .filter(event -> MINOR_SELF_LOGIN.name().equals(event.getType()))
        .filter(event -> MINOR.equals(event.getPrincipal()))
        .toList();
  }

  private String handoverTokenFor(String personalCode) {
    return Jwts.builder()
        .subject(personalCode)
        .claim("tokenType", "HANDOVER")
        .claim("firstName", "Mari")
        .claim("lastName", "Maasikas")
        .issuer("PARTNER AS")
        .claim("authenticationMethod", "SMART_ID")
        .claim("signingMethod", "SMART_ID")
        .claim("phoneNumber", "+37255555555")
        .claim("documentNumber", "PNOEE-" + personalCode + "-MOCK-Q")
        .issuedAt(Date.from(clock.instant()))
        .expiration(Date.from(clock.instant().plusSeconds(300)))
        .signWith(PARTNER_KEYS.getPrivate(), Jwts.SIG.RS256)
        .compact();
  }

  private static AuthenticationIdentity identityOf(String personalCode) {
    var identity = new AuthenticationIdentity();
    identity.setIdentityNumber(personalCode);
    identity.setGivenName("MARI");
    identity.setSurname("MAASIKAS");
    identity.setCountry("EE");
    return identity;
  }

  private static MockHttpServletRequestBuilder authorized(
      MockHttpServletRequestBuilder request, String accessToken) {
    return request.header("Authorization", "Bearer " + accessToken);
  }

  private static Cookie sessionCookie(MvcResult result) {
    Cookie cookie = result.getResponse().getCookie("SESSION");
    assertThat(cookie).isNotNull();
    return cookie;
  }

  private String authenticationHashOf(MvcResult start) throws Exception {
    return objectMapper
        .readTree(start.getResponse().getContentAsString())
        .required("authenticationHash")
        .asText();
  }

  private String accessToken(MvcResult result) throws Exception {
    return objectMapper
        .readTree(result.getResponse().getContentAsString())
        .get("access_token")
        .asText();
  }

  private String refreshToken(MvcResult result) throws Exception {
    return objectMapper
        .readTree(result.getResponse().getContentAsString())
        .get("refresh_token")
        .asText();
  }

  private JsonNode claimsOf(String accessToken) {
    return objectMapper.readTree(Base64.getUrlDecoder().decode(accessToken.split("\\.")[1]));
  }
}
