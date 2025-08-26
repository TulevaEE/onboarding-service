package ee.tuleva.onboarding.auth.partner;

import static ee.tuleva.onboarding.auth.GrantType.PARTNER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ee.tuleva.onboarding.aml.AmlAutoChecker;
import ee.tuleva.onboarding.aml.AmlService;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.cashflows.CashFlowStatement;
import ee.tuleva.onboarding.epis.contact.ContactDetails;
import ee.tuleva.onboarding.event.EventLog;
import ee.tuleva.onboarding.event.EventLogRepository;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserDetailsUpdater;
import ee.tuleva.onboarding.user.UserRepository;
import io.jsonwebtoken.Jwts;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureMockMvc
@Transactional
@Import(HandoverTokenLoginEventIntegrationTest.TestConfig.class)
public class HandoverTokenLoginEventIntegrationTest {

  private static final KeyPair TEST_KEY_PAIR;

  static {
    try {
      KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
      keyPairGenerator.initialize(2048);
      TEST_KEY_PAIR = keyPairGenerator.generateKeyPair();
    } catch (Exception e) {
      throw new RuntimeException("Failed to generate test key pair", e);
    }
  }

  @TestConfiguration
  static class TestConfig {
    // Override the partner public keys with test keys
    @Bean
    public PublicKey partnerPublicKey1() {
      return TEST_KEY_PAIR.getPublic();
    }

    @Bean
    public PublicKey partnerPublicKey2() {
      return TEST_KEY_PAIR.getPublic();
    }
  }

  @Autowired private MockMvc mockMvc;

  @Autowired private EventLogRepository eventLogRepository;

  @Autowired private Clock clock;

  @Autowired private UserRepository userRepository;

  @MockitoBean private EpisService episService;

  @MockitoBean private AmlService amlService;

  @MockitoBean private AmlAutoChecker amlAutoChecker;

  @MockitoBean private UserDetailsUpdater userDetailsUpdater;

  private final KeyPair testKeyPair = TEST_KEY_PAIR;
  private final String testPersonalCode = "38812121215";
  private final String testFirstName = "Mart";
  private final String testLastName = "Tamm";
  private final String testIssuer = "testpartner";

  @BeforeEach
  public void setUp() throws Exception {
    eventLogRepository.deleteAll();

    User testUser =
        User.builder()
            .personalCode(testPersonalCode)
            .firstName(testFirstName)
            .lastName(testLastName)
            .active(true)
            .build();
    userRepository.save(testUser);

    ContactDetails mockContactDetails = ContactDetails.builder().isSecondPillarActive(true).build();
    when(episService.getContactDetails(any())).thenReturn(mockContactDetails);

    CashFlowStatement mockCashFlow =
        CashFlowStatement.builder().transactions(Collections.emptyList()).build();
    when(episService.getCashFlowStatement(any(), any(), any())).thenReturn(mockCashFlow);

    doNothing().when(amlAutoChecker).afterLogin(any());

    doNothing().when(userDetailsUpdater).onAfterTokenGrantedEvent(any());
  }

  private EventLog findLoginEvent(Iterable<EventLog> eventLogs) {
    for (EventLog event : eventLogs) {
      if ("LOGIN".equals(event.getType())) {
        return event;
      }
    }
    return null;
  }

  @Test
  public void whenLoginWithHandoverToken_fromSmartId_thenTrackableEventSavedWithAllData()
      throws Exception {
    // Given - Create HANDOVER token with Smart-ID authentication
    String documentNumber = "PNOEE-30303039816-MOCK-Q";
    String handoverToken = createHandoverToken("SMART_ID", "SMART_ID", documentNumber, null);

    // When - Login with HANDOVER token
    mockMvc
        .perform(
            post("/oauth/token")
                .param("grant_type", PARTNER.name())
                .param("authenticationHash", handoverToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.access_token").exists())
        .andExpect(jsonPath("$.refresh_token").exists());

    // Then - Verify TrackableEvent was saved with all expected data
    Iterable<EventLog> eventLogs = eventLogRepository.findAll();
    assertThat(eventLogs).hasSize(2);

    EventLog loginEvent = findLoginEvent(eventLogs);
    assertThat(loginEvent).isNotNull();
    assertThat(loginEvent.getPrincipal()).isEqualTo(testPersonalCode);
    assertThat(loginEvent.getTimestamp()).isNotNull().isBeforeOrEqualTo(Instant.now());

    // Verify all HANDOVER token specific data
    Map<String, Object> data = loginEvent.getData();
    assertThat(data).containsEntry("method", PARTNER);
    assertThat(data).containsEntry("grantType", "PARTNER");
    assertThat(data).containsEntry("issuer", testIssuer);
    assertThat(data).containsEntry("partnerAuthenticationMethod", "SMART_ID");
    assertThat(data).containsEntry("partnerSigningMethod", "SMART_ID");
    assertThat(data).containsEntry("documentNumber", documentNumber);
    assertThat(data).containsKey("phoneNumber");
    assertThat(data.get("phoneNumber")).isNull();

    // Verify conversion metadata fields are present
    assertThat(data)
        .containsKeys(
            "isSecondPillarActive",
            "isSecondPillarFullyConverted",
            "isSecondPillarPartiallyConverted",
            "isThirdPillarActive",
            "isThirdPillarFullyConverted",
            "isThirdPillarPartiallyConverted");
  }

  @Test
  public void whenLoginWithHandoverToken_fromMobileId_thenTrackableEventSavedWithPhoneNumber()
      throws Exception {
    // Given - Create HANDOVER token with Mobile-ID authentication
    String phoneNumber = "+37255555555";
    String handoverToken = createHandoverToken("MOBILE_ID", "MOBILE_ID", null, phoneNumber);

    // When - Login with HANDOVER token
    mockMvc
        .perform(
            post("/oauth/token")
                .param("grant_type", PARTNER.name())
                .param("authenticationHash", handoverToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.access_token").exists())
        .andExpect(jsonPath("$.refresh_token").exists());

    // Then - Verify TrackableEvent was saved with Mobile-ID specific data
    Iterable<EventLog> eventLogs = eventLogRepository.findAll();
    assertThat(eventLogs).hasSize(2);

    EventLog loginEvent = findLoginEvent(eventLogs);
    assertThat(loginEvent).isNotNull();
    Map<String, Object> data = loginEvent.getData();

    // Verify Mobile-ID specific fields
    assertThat(data).containsEntry("partnerAuthenticationMethod", "MOBILE_ID");
    assertThat(data).containsEntry("partnerSigningMethod", "MOBILE_ID");
    assertThat(data).containsEntry("phoneNumber", phoneNumber);
    assertThat(data).containsKey("documentNumber");
    assertThat(data.get("documentNumber")).isNull();
  }

  @Test
  public void whenLoginWithHandoverToken_fromDifferentIssuer_thenIssuerLoggedCorrectly()
      throws Exception {
    // Given - Create HANDOVER token with different issuer
    String differentIssuer = "another-issuer";
    String handoverToken =
        createHandoverTokenWithCustomIssuer(
            differentIssuer, "SMART_ID", "SMART_ID", "PNOEE-30303039816-MOCK-Q", null);

    // When - Login with HANDOVER token
    mockMvc
        .perform(
            post("/oauth/token")
                .param("grant_type", PARTNER.name())
                .param("authenticationHash", handoverToken))
        .andExpect(status().isOk());

    // Then - Verify the correct issuer is logged
    Iterable<EventLog> eventLogs = eventLogRepository.findAll();
    assertThat(eventLogs).hasSize(2);

    EventLog loginEvent = findLoginEvent(eventLogs);
    assertThat(loginEvent).isNotNull();
    Map<String, Object> data = loginEvent.getData();
    assertThat(data).containsEntry("issuer", differentIssuer);
  }

  private String createHandoverToken(
      String authMethod, String signingMethod, String documentNumber, String phoneNumber) {
    return createHandoverTokenWithCustomIssuer(
        testIssuer, authMethod, signingMethod, documentNumber, phoneNumber);
  }

  private String createHandoverTokenWithCustomIssuer(
      String issuer,
      String authMethod,
      String signingMethod,
      String documentNumber,
      String phoneNumber) {
    var builder =
        Jwts.builder()
            .subject(testPersonalCode)
            .signWith(testKeyPair.getPrivate())
            .expiration(Date.from(clock.instant().plusSeconds(3600)))
            .claim("tokenType", "HANDOVER")
            .claim("firstName", testFirstName)
            .claim("lastName", testLastName)
            .claim("iss", issuer)
            .claim("authenticationMethod", authMethod)
            .claim("signingMethod", signingMethod);

    if (documentNumber != null) {
      builder.claim("documentNumber", documentNumber);
    }
    if (phoneNumber != null) {
      builder.claim("phoneNumber", phoneNumber);
    }

    return builder.compact();
  }
}
