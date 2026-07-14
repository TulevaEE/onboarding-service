package ee.tuleva.onboarding.auth.smartid;

import static ee.tuleva.onboarding.aml.AmlCheckType.SK_NAME;
import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.firstName;
import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.lastName;
import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.personalCode;
import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ee.sk.smartid.AuthenticationHash;
import ee.sk.smartid.AuthenticationIdentity;
import ee.sk.smartid.AuthenticationResponseValidator;
import ee.sk.smartid.SmartIdClient;
import ee.sk.smartid.rest.SmartIdConnector;
import ee.sk.smartid.rest.dao.AuthenticationSessionResponse;
import ee.sk.smartid.rest.dao.SemanticsIdentifier;
import ee.sk.smartid.rest.dao.SessionCertificate;
import ee.sk.smartid.rest.dao.SessionResult;
import ee.sk.smartid.rest.dao.SessionSignature;
import ee.sk.smartid.rest.dao.SessionStatus;
import ee.tuleva.onboarding.aml.AmlCheck;
import ee.tuleva.onboarding.aml.AmlCheckRepository;
import ee.tuleva.onboarding.auth.principal.PersonImpl;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserRepository;
import jakarta.servlet.http.Cookie;
import java.security.KeyStore;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "mock"})
@Import(SmartIdAuthIntegrationTest.SmartIdTestConfig.class)
@Transactional
class SmartIdAuthIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private JsonMapper objectMapper;
  @Autowired private UserRepository userRepository;
  @Autowired private AmlCheckRepository amlCheckRepository;

  @MockitoBean private SmartIdConnector smartIdConnector;
  @MockitoBean private AuthenticationResponseValidator authenticationResponseValidator;
  @MockitoBean private SmartIdAuthenticationHashGenerator hashGenerator;

  // SK demo CA test certificate (PNOEE-31111111111). Used only so the SDK's internal
  // X509 parsing succeeds; the AuthenticationResponseValidator is mocked to bypass real
  // signature/chain verification.
  private static final String SAMPLE_TEST_CERT =
      "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";

  @TestConfiguration
  static class SmartIdTestConfig {
    @Bean
    @Primary
    SmartIdClient testSmartIdClient(
        SmartIdConnector connector, @Qualifier("trustStore") KeyStore trustStore) {
      var client = new SmartIdClient();
      client.setSmartIdConnector(connector);
      client.setTrustStore(trustStore);
      client.setRelyingPartyUUID("00000000-0000-0000-0000-000000000000");
      client.setRelyingPartyName("Test");
      return client;
    }
  }

  @Test
  void smartIdLoginCompletesEndToEnd() throws Exception {
    var hash = AuthenticationHash.generateRandomHash();
    given(hashGenerator.generateHash()).willReturn(hash);
    given(smartIdConnector.authenticate(any(SemanticsIdentifier.class), any()))
        .willReturn(authenticationSessionResponse("test-session-id"));
    given(smartIdConnector.getSessionStatus(eq("test-session-id")))
        .willReturn(completeSessionStatus());
    given(authenticationResponseValidator.validate(any())).willReturn(validIdentity());

    var authResult =
        mockMvc
            .perform(
                post("/authenticate")
                    .contentType(APPLICATION_JSON)
                    .content("{\"type\":\"SMART_ID\",\"personalCode\":\"" + personalCode + "\"}"))
            .andExpect(status().isOk())
            .andExpect(cookie().exists("SESSION"))
            .andReturn();

    var sessionCookie = authResult.getResponse().getCookie("SESSION");
    assertThat(sessionCookie).isNotNull();
    var authHash =
        objectMapper
            .readTree(authResult.getResponse().getContentAsString())
            .get("authenticationHash")
            .asText();

    await()
        .pollInSameThread() // keep test transaction visible to /oauth/token's MockMvc call
        .atMost(ofSeconds(5))
        .untilAsserted(
            () ->
                mockMvc
                    .perform(
                        post("/oauth/token")
                            .cookie(sessionCookie)
                            .param("grant_type", "SMART_ID")
                            .param("authenticationHash", authHash))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.access_token").isNotEmpty()));
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

    String accessToken = completeSmartIdLogin(identity("AADU", "KUUSK-ÕUNAPUU"));

    User user = userRepository.findByPersonalCode(personalCode).orElseThrow();
    assertThat(user.getFirstName()).isEqualTo("Aadu");
    assertThat(user.getLastName()).isEqualTo("Kuusk-Õunapuu");

    var claims = objectMapper.readTree(Base64.getUrlDecoder().decode(accessToken.split("\\.")[1]));
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

    completeSmartIdLogin(identity("AADU", "KUUSK-ÕUNAPUU"));

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

  private String completeSmartIdLogin(AuthenticationIdentity identity) throws Exception {
    var hash = AuthenticationHash.generateRandomHash();
    given(hashGenerator.generateHash()).willReturn(hash);
    given(smartIdConnector.authenticate(any(SemanticsIdentifier.class), any()))
        .willReturn(authenticationSessionResponse("test-session-id"));
    given(smartIdConnector.getSessionStatus(eq("test-session-id")))
        .willReturn(completeSessionStatus());
    given(authenticationResponseValidator.validate(any())).willReturn(identity);

    var authResult =
        mockMvc
            .perform(
                post("/authenticate")
                    .contentType(APPLICATION_JSON)
                    .content("{\"type\":\"SMART_ID\",\"personalCode\":\"" + personalCode + "\"}"))
            .andExpect(status().isOk())
            .andReturn();

    Cookie sessionCookie = authResult.getResponse().getCookie("SESSION");
    assertThat(sessionCookie).isNotNull();
    var authHash =
        objectMapper
            .readTree(authResult.getResponse().getContentAsString())
            .get("authenticationHash")
            .asText();

    var accessToken = new AtomicReference<String>();
    await()
        .pollInSameThread() // keep test transaction visible to /oauth/token's MockMvc call
        .atMost(ofSeconds(5))
        .untilAsserted(
            () -> {
              var result =
                  mockMvc
                      .perform(
                          post("/oauth/token")
                              .cookie(sessionCookie)
                              .param("grant_type", "SMART_ID")
                              .param("authenticationHash", authHash))
                      .andReturn();
              assertThat(result.getResponse().getStatus()).isEqualTo(200);
              accessToken.set(
                  objectMapper
                      .readTree(result.getResponse().getContentAsString())
                      .get("access_token")
                      .asText());
            });
    return accessToken.get();
  }

  private static AuthenticationIdentity identity(String givenName, String surname) {
    var identity = new AuthenticationIdentity();
    identity.setIdentityCode(personalCode);
    identity.setGivenName(givenName);
    identity.setSurname(surname);
    identity.setCountry("EE");
    return identity;
  }

  private static AuthenticationSessionResponse authenticationSessionResponse(String sessionId) {
    var response = new AuthenticationSessionResponse();
    response.setSessionID(sessionId);
    return response;
  }

  private static AuthenticationIdentity validIdentity() {
    return identity(firstName, lastName);
  }

  private static SessionStatus completeSessionStatus() {
    var result = new SessionResult();
    result.setEndResult("OK");
    result.setDocumentNumber("PNOEE-372123456");

    var signature = new SessionSignature();
    signature.setAlgorithm("sha256WithRSAEncryption");
    signature.setValue("dummy-signature");

    var certificate = new SessionCertificate();
    certificate.setCertificateLevel("QUALIFIED");
    certificate.setValue(SAMPLE_TEST_CERT);

    var status = new SessionStatus();
    status.setState("COMPLETE");
    status.setResult(result);
    status.setSignature(signature);
    status.setCert(certificate);
    return status;
  }
}
