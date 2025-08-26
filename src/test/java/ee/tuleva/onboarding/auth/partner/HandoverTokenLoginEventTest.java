package ee.tuleva.onboarding.auth.partner;

import static ee.tuleva.onboarding.auth.GrantType.PARTNER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.auth.AuthenticationTokens;
import ee.tuleva.onboarding.auth.authority.GrantedAuthorityFactory;
import ee.tuleva.onboarding.auth.event.AfterTokenGrantedEvent;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.auth.principal.PrincipalService;
import ee.tuleva.onboarding.conversion.ConversionResponse;
import ee.tuleva.onboarding.conversion.UserConversionService;
import ee.tuleva.onboarding.epis.contact.ContactDetails;
import ee.tuleva.onboarding.epis.contact.ContactDetailsService;
import ee.tuleva.onboarding.event.TrackableEvent;
import ee.tuleva.onboarding.event.TrackableEventType;
import ee.tuleva.onboarding.event.broadcasting.LoginEventBroadcaster;
import ee.tuleva.onboarding.mandate.builder.ConversionDecorator;
import io.jsonwebtoken.Jwts;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
public class HandoverTokenLoginEventTest {

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

  @Mock private PrincipalService principalService;
  @Mock private ApplicationEventPublisher eventPublisher;
  @Mock private UserConversionService conversionService;
  @Mock private ContactDetailsService contactDetailsService;
  @Mock private ConversionDecorator conversionDecorator;
  @Mock private AuthenticatedPerson authenticatedPerson;
  @Mock private GrantedAuthorityFactory grantedAuthorityFactory;

  private PartnerAuthProvider partnerAuthProvider;
  private LoginEventBroadcaster loginEventBroadcaster;
  private Clock clock;

  private final String testPersonalCode = "38812121215";
  private final String testFirstName = "Mart";
  private final String testLastName = "Tamm";
  private final String testIssuer = "testpartner";
  private final Instant fixedInstant = Instant.parse("2024-01-01T12:00:00Z");

  @BeforeEach
  public void setUp() {
    clock = Clock.fixed(fixedInstant, ZoneId.of("UTC"));

    partnerAuthProvider =
        new PartnerAuthProvider(
            TEST_KEY_PAIR.getPublic(), TEST_KEY_PAIR.getPublic(), clock, principalService);

    loginEventBroadcaster =
        new LoginEventBroadcaster(
            eventPublisher,
            conversionService,
            contactDetailsService,
            conversionDecorator,
            grantedAuthorityFactory);

    when(authenticatedPerson.getPersonalCode()).thenReturn(testPersonalCode);

    when(grantedAuthorityFactory.from(any(AuthenticatedPerson.class)))
        .thenReturn(Collections.emptyList());
  }

  @Test
  @DisplayName("Login event contains all Smart-ID authentication data")
  public void whenLoginWithHandoverToken_fromSmartId_thenLoginEventPublishedWithAllData() {
    // Given - Smart-ID authentication token
    String documentNumber = "PNOEE-30303039816-MOCK-Q";
    String handoverToken = createHandoverToken("SMART_ID", "SMART_ID", documentNumber, null);

    Map<String, String> expectedAttributes = new HashMap<>();
    expectedAttributes.put("grantType", "PARTNER");
    expectedAttributes.put("issuer", testIssuer);
    expectedAttributes.put("partnerAuthenticationMethod", "SMART_ID");
    expectedAttributes.put("partnerSigningMethod", "SMART_ID");
    expectedAttributes.put("documentNumber", documentNumber);
    expectedAttributes.put("phoneNumber", null);

    when(authenticatedPerson.getAttributes()).thenReturn(expectedAttributes);

    when(principalService.getFrom(any(Person.class), anyMap())).thenReturn(authenticatedPerson);

    ConversionResponse.Conversion secondPillar =
        ConversionResponse.Conversion.builder()
            .transfersComplete(false)
            .selectionComplete(true)
            .transfersPartial(true)
            .selectionPartial(false)
            .build();
    ConversionResponse.Conversion thirdPillar =
        ConversionResponse.Conversion.builder()
            .transfersComplete(false)
            .selectionComplete(false)
            .transfersPartial(false)
            .selectionPartial(false)
            .build();
    ConversionResponse mockConversion =
        ConversionResponse.builder().secondPillar(secondPillar).thirdPillar(thirdPillar).build();
    when(conversionService.getConversion(authenticatedPerson)).thenReturn(mockConversion);

    ContactDetails mockContactDetails = mock(ContactDetails.class);
    when(contactDetailsService.getContactDetails(authenticatedPerson))
        .thenReturn(mockContactDetails);

    doAnswer(
            invocation -> {
              Map<String, Object> data = invocation.getArgument(0);
              data.put("isSecondPillarActive", true);
              data.put("isSecondPillarFullyConverted", false);
              data.put("isSecondPillarPartiallyConverted", true);
              data.put("isThirdPillarActive", false);
              data.put("isThirdPillarFullyConverted", false);
              data.put("isThirdPillarPartiallyConverted", false);
              return null;
            })
        .when(conversionDecorator)
        .addConversionMetadata(any(), any(), any(), any());

    // When - authentication and login event
    partnerAuthProvider.authenticate(handoverToken);

    AuthenticationTokens tokens = new AuthenticationTokens("mock-access", "mock-refresh");
    AfterTokenGrantedEvent afterTokenEvent =
        new AfterTokenGrantedEvent(this, authenticatedPerson, PARTNER, tokens);
    loginEventBroadcaster.onAfterTokenGrantedEvent(afterTokenEvent);

    // Then
    ArgumentCaptor<TrackableEvent> eventCaptor = ArgumentCaptor.forClass(TrackableEvent.class);
    verify(eventPublisher).publishEvent(eventCaptor.capture());

    TrackableEvent loginEvent = eventCaptor.getValue();
    assertThat(loginEvent.getType()).isEqualTo(TrackableEventType.LOGIN);
    assertThat(loginEvent.getPerson()).isEqualTo(authenticatedPerson);

    Map<String, Object> eventData = loginEvent.getData();
    assertThat(eventData).containsEntry("method", PARTNER);
    assertThat(eventData).containsEntry("grantType", "PARTNER");
    assertThat(eventData).containsEntry("issuer", testIssuer);
    assertThat(eventData).containsEntry("partnerAuthenticationMethod", "SMART_ID");
    assertThat(eventData).containsEntry("partnerSigningMethod", "SMART_ID");
    assertThat(eventData).containsEntry("documentNumber", documentNumber);
    assertThat(eventData).containsEntry("phoneNumber", null);

    assertThat(eventData).containsEntry("isSecondPillarActive", true);
    assertThat(eventData).containsEntry("isSecondPillarFullyConverted", false);
    assertThat(eventData).containsEntry("isSecondPillarPartiallyConverted", true);
  }

  @Test
  @DisplayName("Login event contains phone number for Mobile-ID authentication")
  public void whenLoginWithHandoverToken_fromMobileId_thenLoginEventPublishedWithPhoneNumber() {
    // Given - Mobile-ID authentication token
    String phoneNumber = "+37255555555";
    String handoverToken = createHandoverToken("MOBILE_ID", "MOBILE_ID", null, phoneNumber);

    Map<String, String> expectedAttributes = new HashMap<>();
    expectedAttributes.put("grantType", "PARTNER");
    expectedAttributes.put("issuer", testIssuer);
    expectedAttributes.put("partnerAuthenticationMethod", "MOBILE_ID");
    expectedAttributes.put("partnerSigningMethod", "MOBILE_ID");
    expectedAttributes.put("documentNumber", null);
    expectedAttributes.put("phoneNumber", phoneNumber);

    when(authenticatedPerson.getAttributes()).thenReturn(expectedAttributes);

    when(principalService.getFrom(any(Person.class), anyMap())).thenReturn(authenticatedPerson);

    ConversionResponse.Conversion secondPillar =
        ConversionResponse.Conversion.builder()
            .transfersComplete(false)
            .selectionComplete(false)
            .build();
    ConversionResponse.Conversion thirdPillar =
        ConversionResponse.Conversion.builder()
            .transfersComplete(false)
            .selectionComplete(false)
            .build();
    ConversionResponse mockConversion =
        ConversionResponse.builder().secondPillar(secondPillar).thirdPillar(thirdPillar).build();
    when(conversionService.getConversion(authenticatedPerson)).thenReturn(mockConversion);

    ContactDetails mockContactDetails = mock(ContactDetails.class);
    when(contactDetailsService.getContactDetails(authenticatedPerson))
        .thenReturn(mockContactDetails);

    // When - authentication and login event
    partnerAuthProvider.authenticate(handoverToken);

    AuthenticationTokens tokens = new AuthenticationTokens("mock-access", "mock-refresh");
    AfterTokenGrantedEvent afterTokenEvent =
        new AfterTokenGrantedEvent(this, authenticatedPerson, PARTNER, tokens);
    loginEventBroadcaster.onAfterTokenGrantedEvent(afterTokenEvent);

    // Then
    ArgumentCaptor<TrackableEvent> eventCaptor = ArgumentCaptor.forClass(TrackableEvent.class);
    verify(eventPublisher).publishEvent(eventCaptor.capture());

    TrackableEvent loginEvent = eventCaptor.getValue();
    Map<String, Object> eventData = loginEvent.getData();

    assertThat(eventData).containsEntry("partnerAuthenticationMethod", "MOBILE_ID");
    assertThat(eventData).containsEntry("partnerSigningMethod", "MOBILE_ID");
    assertThat(eventData).containsEntry("phoneNumber", phoneNumber);
    assertThat(eventData).containsEntry("documentNumber", null);
  }

  @Test
  @DisplayName("Login event logs issuer correctly for different token issuers")
  public void whenLoginWithHandoverToken_fromDifferentIssuer_thenIssuerLoggedCorrectly() {
    // Given - token from different issuer
    String differentIssuer = "another-issuer";
    String handoverToken =
        createHandoverTokenWithCustomIssuer(
            differentIssuer, "SMART_ID", "SMART_ID", "PNOEE-30303039816-MOCK-Q", null);

    Map<String, String> expectedAttributes = new HashMap<>();
    expectedAttributes.put("grantType", "PARTNER");
    expectedAttributes.put("issuer", differentIssuer);
    expectedAttributes.put("partnerAuthenticationMethod", "SMART_ID");
    expectedAttributes.put("partnerSigningMethod", "SMART_ID");
    expectedAttributes.put("documentNumber", "PNOEE-30303039816-MOCK-Q");
    expectedAttributes.put("phoneNumber", null);

    when(authenticatedPerson.getAttributes()).thenReturn(expectedAttributes);

    when(principalService.getFrom(any(Person.class), anyMap())).thenReturn(authenticatedPerson);

    ConversionResponse.Conversion secondPillar =
        ConversionResponse.Conversion.builder()
            .transfersComplete(false)
            .selectionComplete(false)
            .build();
    ConversionResponse.Conversion thirdPillar =
        ConversionResponse.Conversion.builder()
            .transfersComplete(false)
            .selectionComplete(false)
            .build();
    ConversionResponse mockConversion =
        ConversionResponse.builder().secondPillar(secondPillar).thirdPillar(thirdPillar).build();
    when(conversionService.getConversion(authenticatedPerson)).thenReturn(mockConversion);

    ContactDetails mockContactDetails = mock(ContactDetails.class);
    when(contactDetailsService.getContactDetails(authenticatedPerson))
        .thenReturn(mockContactDetails);

    // When - authentication and login event
    partnerAuthProvider.authenticate(handoverToken);

    AuthenticationTokens tokens = new AuthenticationTokens("mock-access", "mock-refresh");
    AfterTokenGrantedEvent afterTokenEvent =
        new AfterTokenGrantedEvent(this, authenticatedPerson, PARTNER, tokens);
    loginEventBroadcaster.onAfterTokenGrantedEvent(afterTokenEvent);

    // Then
    ArgumentCaptor<TrackableEvent> eventCaptor = ArgumentCaptor.forClass(TrackableEvent.class);
    verify(eventPublisher).publishEvent(eventCaptor.capture());

    TrackableEvent loginEvent = eventCaptor.getValue();
    Map<String, Object> eventData = loginEvent.getData();
    assertThat(eventData).containsEntry("issuer", differentIssuer);
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
            .signWith(TEST_KEY_PAIR.getPrivate())
            .expiration(Date.from(clock.instant().plusSeconds(3600)))
            .claim("tokenType", "HANDOVER")
            .claim("firstName", testFirstName)
            .claim("lastName", testLastName)
            .issuer(issuer)
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
