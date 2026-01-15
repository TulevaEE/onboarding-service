package ee.tuleva.onboarding.aml;

import static ee.tuleva.onboarding.aml.AmlCheckType.*;
import static ee.tuleva.onboarding.conversion.ConversionResponseFixture.*;
import static ee.tuleva.onboarding.mandate.MandateFixture.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.quality.Strictness.LENIENT;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ee.tuleva.onboarding.aml.notification.AmlCheckCreatedEvent;
import ee.tuleva.onboarding.aml.notification.AmlChecksRunEvent;
import ee.tuleva.onboarding.aml.sanctions.MatchResponse;
import ee.tuleva.onboarding.aml.sanctions.PepAndSanctionCheckService;
import ee.tuleva.onboarding.analytics.thirdpillar.AnalyticsRecentThirdPillar;
import ee.tuleva.onboarding.analytics.thirdpillar.AnalyticsRecentThirdPillarRepository;
import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.auth.principal.PersonImpl;
import ee.tuleva.onboarding.conversion.ConversionResponse;
import ee.tuleva.onboarding.conversion.UserConversionService;
import ee.tuleva.onboarding.country.Country;
import ee.tuleva.onboarding.epis.contact.ContactDetails;
import ee.tuleva.onboarding.event.TrackableEvent;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.time.ClockHolder;
import ee.tuleva.onboarding.user.User;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.context.ApplicationEventPublisher;

@MockitoSettings(strictness = LENIENT)
@ExtendWith(MockitoExtension.class)
class AmlServiceTest {

  @Mock private AmlCheckRepository amlCheckRepository;
  @Mock private ApplicationEventPublisher eventPublisher;
  @Mock private PepAndSanctionCheckService pepAndSanctionCheckService;
  @Mock private AnalyticsRecentThirdPillarRepository analyticsRecentThirdPillarRepository;
  @Mock private UserConversionService userConversionService;

  @InjectMocks private AmlService amlService;

  @Captor private ArgumentCaptor<AmlCheck> amlCheckCaptor;
  @Captor private ArgumentCaptor<TrackableEvent> trackableEventCaptor;

  private static final ObjectMapper objectMapper = new ObjectMapper();
  private static final Instant FIXED_INSTANT = Instant.parse("2020-11-23T10:00:00Z");
  private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
  private Instant aYearAgoFromTestClock;

  enum Checks {
    REQUIRED(true),
    NOT_REQUIRED(false);

    private final boolean checksRequired;

    Checks(boolean required) {
      this.checksRequired = required;
    }
  }

  private User createUser(String personalCode, String firstName, String lastName, Long id) {
    User user = mock(User.class);
    when(user.getPersonalCode()).thenReturn(personalCode);
    when(user.getFirstName()).thenReturn(firstName);
    when(user.getLastName()).thenReturn(lastName);
    when(user.getId()).thenReturn(id);
    return user;
  }

  private ContactDetails createContactDetails(
      String personalCode, String firstName, String lastName) {
    ContactDetails contactDetails = mock(ContactDetails.class);
    when(contactDetails.getPersonalCode()).thenReturn(personalCode);
    when(contactDetails.getFirstName()).thenReturn(firstName);
    when(contactDetails.getLastName()).thenReturn(lastName);
    return contactDetails;
  }

  @BeforeEach
  void setUp() {
    ClockHolder.setClock(FIXED_CLOCK);
    aYearAgoFromTestClock = FIXED_INSTANT.minus(365, ChronoUnit.DAYS);
    lenient()
        .when(amlCheckRepository.save(any(AmlCheck.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  @AfterEach
  void tearDown() {
    ClockHolder.setDefaultClock();
  }

  @Test
  @DisplayName("checkUserBeforeLogin: adds document, SK name, and residency checks when resident")
  void checkUserBeforeLogin_addsAllChecks_whenResident() {
    // given
    User testUser = createUser("12345", "Test", "User", 1L);
    Boolean isResident = true;
    when(amlCheckRepository.existsByPersonalCodeAndTypeAndCreatedTimeAfter(
            anyString(), any(AmlCheckType.class), any(Instant.class)))
        .thenReturn(false);

    // when
    amlService.checkUserBeforeLogin(testUser, testUser, isResident);

    // then
    verify(amlCheckRepository, times(3)).save(amlCheckCaptor.capture());
    List<AmlCheck> savedChecks = amlCheckCaptor.getAllValues();

    assertTrue(
        savedChecks.stream()
            .anyMatch(
                c ->
                    c.getType() == DOCUMENT
                        && c.isSuccess()
                        && c.getPersonalCode().equals("12345")));
    assertTrue(
        savedChecks.stream()
            .anyMatch(
                c ->
                    c.getType() == RESIDENCY_AUTO
                        && c.isSuccess()
                        && c.getPersonalCode().equals("12345")));
    AmlCheck skNameCheck =
        savedChecks.stream().filter(c -> c.getType() == SK_NAME).findFirst().orElseThrow();
    assertTrue(skNameCheck.isSuccess());
    assertEquals("12345", skNameCheck.getPersonalCode());
    assertNotNull(skNameCheck.getMetadata());
    assertEquals(new PersonImpl(testUser), skNameCheck.getMetadata().get("user"));
    assertEquals(new PersonImpl(testUser), skNameCheck.getMetadata().get("person"));

    verify(eventPublisher, times(3)).publishEvent(any(AmlCheckCreatedEvent.class));
  }

  @Test
  @DisplayName("checkUserBeforeLogin: does not add residency check if isResident is null")
  void checkUserBeforeLogin_noResidencyCheck_whenIsNull() {
    // given
    User testUser = createUser("12345", "Test", "User", 1L);
    Boolean isResident = null;
    when(amlCheckRepository.existsByPersonalCodeAndTypeAndCreatedTimeAfter(
            anyString(), any(AmlCheckType.class), any(Instant.class)))
        .thenReturn(false);

    // when
    amlService.checkUserBeforeLogin(testUser, testUser, isResident);

    // then
    verify(amlCheckRepository, times(2)).save(amlCheckCaptor.capture());
    List<AmlCheck> savedChecks = amlCheckCaptor.getAllValues();
    assertTrue(savedChecks.stream().anyMatch(c -> c.getType() == DOCUMENT));
    assertTrue(savedChecks.stream().anyMatch(c -> c.getType() == SK_NAME));
    assertFalse(savedChecks.stream().anyMatch(c -> c.getType() == RESIDENCY_AUTO));
  }

  @Test
  @DisplayName("checkUserBeforeLogin: SK name check fails if last names differ")
  void checkUserBeforeLogin_skNameCheckFails_onLastNameMismatch() {
    // given
    User user = createUser("12345", "Test", "User", 1L);
    Person person = createUser("12345", "Test", "DifferentUser", 1L);
    Boolean isResident = true;
    when(amlCheckRepository.existsByPersonalCodeAndTypeAndCreatedTimeAfter(
            anyString(), any(AmlCheckType.class), any(Instant.class)))
        .thenReturn(false);

    // when
    amlService.checkUserBeforeLogin(user, person, isResident);

    // then
    verify(amlCheckRepository, times(3)).save(amlCheckCaptor.capture());
    AmlCheck skNameCheck =
        amlCheckCaptor.getAllValues().stream()
            .filter(c -> c.getType() == SK_NAME)
            .findFirst()
            .orElseThrow();
    assertFalse(skNameCheck.isSuccess(), "SK Name check should fail due to last name mismatch");
  }

  @Test
  @DisplayName("addPensionRegistryNameCheckIfMissing: adds check if missing and names match")
  void addPensionRegistryNameCheckIfMissing_addsCheck_whenMissingAndNamesMatch() {
    // given
    User user = createUser("123", "First", "Last", 1L);
    ContactDetails contactDetails = createContactDetails("123", "First", "Last");
    when(amlCheckRepository.existsByPersonalCodeAndTypeAndCreatedTimeAfter(
            "123", PENSION_REGISTRY_NAME, aYearAgoFromTestClock))
        .thenReturn(false);

    // when
    Optional<AmlCheck> result =
        amlService.addPensionRegistryNameCheckIfMissing(user, contactDetails);

    // then
    assertTrue(result.isPresent());
    verify(amlCheckRepository).save(amlCheckCaptor.capture());
    AmlCheck savedCheck = amlCheckCaptor.getValue();
    assertEquals(PENSION_REGISTRY_NAME, savedCheck.getType());
    assertTrue(savedCheck.isSuccess());
    assertEquals("123", savedCheck.getPersonalCode());
    assertEquals(new PersonImpl(user), savedCheck.getMetadata().get("user"));
    assertEquals(new PersonImpl(contactDetails), savedCheck.getMetadata().get("person"));
    verify(eventPublisher).publishEvent(any(AmlCheckCreatedEvent.class));
  }

  @Test
  @DisplayName("addPensionRegistryNameCheckIfMissing: saves failed check if last names differ")
  void addPensionRegistryNameCheckIfMissing_savesFailedCheck_onLastNameMismatch() {
    // given
    User user = createUser("123", "First", "OriginalLast", 1L);
    ContactDetails contactDetails = createContactDetails("123", "First", "DifferentLast");
    when(amlCheckRepository.existsByPersonalCodeAndTypeAndCreatedTimeAfter(
            "123", PENSION_REGISTRY_NAME, aYearAgoFromTestClock))
        .thenReturn(false);

    // when
    Optional<AmlCheck> result =
        amlService.addPensionRegistryNameCheckIfMissing(user, contactDetails);

    // then
    assertTrue(result.isPresent());
    verify(amlCheckRepository).save(amlCheckCaptor.capture());
    AmlCheck savedCheck = amlCheckCaptor.getValue();
    assertEquals(PENSION_REGISTRY_NAME, savedCheck.getType());
    assertFalse(savedCheck.isSuccess(), "Pension registry name check should fail");
  }

  @Test
  @DisplayName("addContactDetailsCheckIfMissing: adds check if missing")
  void addContactDetailsCheckIfMissing_addsCheck() {
    // given
    User user = createUser("123", "First", "Last", 1L);
    when(amlCheckRepository.existsByPersonalCodeAndTypeAndCreatedTimeAfter(
            "123", CONTACT_DETAILS, aYearAgoFromTestClock))
        .thenReturn(false);

    // when
    Optional<AmlCheck> result = amlService.addContactDetailsCheckIfMissing(user);

    // then
    assertTrue(result.isPresent());
    verify(amlCheckRepository).save(amlCheckCaptor.capture());
    AmlCheck savedCheck = amlCheckCaptor.getValue();
    assertEquals(CONTACT_DETAILS, savedCheck.getType());
    assertTrue(savedCheck.isSuccess());
    assertEquals("123", savedCheck.getPersonalCode());
    verify(eventPublisher).publishEvent(any(AmlCheckCreatedEvent.class));
  }

  @Test
  @DisplayName("addCheckIfMissing: adds check when it does not exist")
  void addCheckIfMissing_addsCheck_whenNotExists() {
    // given
    AmlCheck newCheck = AmlCheck.builder().personalCode("123").type(DOCUMENT).success(true).build();
    when(amlCheckRepository.existsByPersonalCodeAndTypeAndCreatedTimeAfter(
            "123", DOCUMENT, aYearAgoFromTestClock))
        .thenReturn(false);

    // when
    Optional<AmlCheck> result = amlService.addCheckIfMissing(newCheck);

    // then
    assertTrue(result.isPresent());
    verify(amlCheckRepository).save(newCheck);
    verify(eventPublisher).publishEvent(any(AmlCheckCreatedEvent.class));
  }

  @Test
  @DisplayName("addCheckIfMissing: does not add check when it already exists")
  void addCheckIfMissing_doesNotAdd_whenExists() {
    // given
    AmlCheck existingCheck =
        AmlCheck.builder().personalCode("123").type(DOCUMENT).success(true).build();
    when(amlCheckRepository.existsByPersonalCodeAndTypeAndCreatedTimeAfter(
            "123", DOCUMENT, aYearAgoFromTestClock))
        .thenReturn(true);

    // when
    Optional<AmlCheck> result = amlService.addCheckIfMissing(existingCheck);

    // then
    assertFalse(result.isPresent());
    verify(amlCheckRepository, never()).save(any());
    verify(eventPublisher, never()).publishEvent(any(AmlCheckCreatedEvent.class));
  }

  @Test
  @DisplayName("getChecks: returns checks from repository")
  void getChecks_returnsAllChecks() {
    // given
    User user = createUser("123", "Test", "User", 1L);
    List<AmlCheck> expectedChecks = List.of(AmlCheck.builder().build());
    when(amlCheckRepository.findAllByPersonalCodeAndCreatedTimeAfter("123", aYearAgoFromTestClock))
        .thenReturn(expectedChecks);

    // when
    List<AmlCheck> actualChecks = amlService.getChecks(user);

    // then
    assertEquals(expectedChecks, actualChecks);
  }

  @Test
  @DisplayName("allChecksPassed: returns true for pillar 2")
  void allChecksPassed_trueForPillar2() {
    // given
    User user = createUser("123", "Test", "User", 1L);
    var mandate = sampleMandate();

    assertEquals(2, mandate.getPillar());

    // when
    boolean result = amlService.allChecksPassed(user, mandate);

    // then
    assertTrue(result);
  }

  private static Stream<Arguments> allChecksPassedThirdPillarScenarios() {
    return Stream.of(
        Arguments.of(Collections.emptyList(), false, true),
        Arguments.of(
            successfulChecks(
                POLITICALLY_EXPOSED_PERSON,
                SANCTION,
                DOCUMENT,
                OCCUPATION,
                RESIDENCY_AUTO,
                SK_NAME),
            true,
            false),
        Arguments.of(
            successfulChecks(
                POLITICALLY_EXPOSED_PERSON,
                SANCTION,
                DOCUMENT,
                OCCUPATION,
                RESIDENCY_MANUAL,
                PENSION_REGISTRY_NAME),
            true,
            false),
        Arguments.of(
            successfulChecks(
                POLITICALLY_EXPOSED_PERSON_AUTO,
                SANCTION,
                DOCUMENT,
                OCCUPATION,
                RESIDENCY_AUTO,
                SK_NAME),
            true,
            false),
        Arguments.of(
            successfulChecks(
                POLITICALLY_EXPOSED_PERSON_OVERRIDE,
                SANCTION,
                DOCUMENT,
                OCCUPATION,
                RESIDENCY_MANUAL,
                PENSION_REGISTRY_NAME),
            true,
            false),
        Arguments.of(
            Stream.concat(
                    successfulChecks(SANCTION, DOCUMENT, OCCUPATION, RESIDENCY_AUTO, SK_NAME)
                        .stream(),
                    failedChecks(POLITICALLY_EXPOSED_PERSON).stream())
                .collect(Collectors.toList()),
            true,
            false), // Failed manual PEP declaration should still allow mandate signing
        Arguments.of(
            successfulChecks(
                POLITICALLY_EXPOSED_PERSON, DOCUMENT, OCCUPATION, RESIDENCY_AUTO, SK_NAME),
            false,
            true), // Missing SANCTION
        Arguments.of(
            successfulChecks(
                POLITICALLY_EXPOSED_PERSON, SANCTION, DOCUMENT, OCCUPATION, RESIDENCY_MANUAL),
            false,
            true), // Missing NAME check
        Arguments.of(
            successfulChecks(
                POLITICALLY_EXPOSED_PERSON_AUTO,
                SANCTION_OVERRIDE,
                DOCUMENT,
                OCCUPATION,
                RESIDENCY_AUTO,
                PENSION_REGISTRY_NAME),
            true,
            false));
  }

  @ParameterizedTest
  @MethodSource("allChecksPassedThirdPillarScenarios")
  @DisplayName("allChecksPassed: evaluates third pillar non-withdrawal checks correctly")
  void allChecksPassed_evaluatesThirdPillar(
      List<AmlCheck> checks, boolean expectedResult, boolean eventExpected) {
    // given
    User user = createUser("12345", "Test", "User", 1L);
    var mandate = thirdPillarMandate();
    assertEquals(3, mandate.getPillar());
    when(amlCheckRepository.findAllByPersonalCodeAndCreatedTimeAfter(
            user.getPersonalCode(), aYearAgoFromTestClock))
        .thenReturn(checks);

    when(userConversionService.getConversion(user)).thenReturn(fullyConverted());

    // when
    boolean actualResult = amlService.allChecksPassed(user, mandate);

    // then
    assertEquals(expectedResult, actualResult);
    if (eventExpected) {
      verify(eventPublisher).publishEvent(trackableEventCaptor.capture());
    } else {
      verify(eventPublisher, never()).publishEvent(any(TrackableEvent.class));
    }
  }

  private static Stream<Arguments> amlChecksRequiredMandateScenarios() {
    var tulevaClientScenarios =
        Stream.of(
            Arguments.of(fullyConverted(), thirdPillarMandate(), Checks.REQUIRED),
            Arguments.of(notFullyConverted(), thirdPillarMandate(), Checks.REQUIRED),
            Arguments.of(fullyConverted(), sampleMandate(), Checks.NOT_REQUIRED),
            Arguments.of(notFullyConverted(), sampleMandate(), Checks.NOT_REQUIRED),
            Arguments.of(
                fullyConverted(),
                samplePartialWithdrawalMandate(aThirdPillarPartialWithdrawalMandateDetails),
                Checks.REQUIRED),
            Arguments.of(
                notFullyConverted(),
                samplePartialWithdrawalMandate(aThirdPillarPartialWithdrawalMandateDetails),
                Checks.REQUIRED),
            Arguments.of(
                fullyConverted(),
                samplePartialWithdrawalMandate(aPartialWithdrawalMandateDetails),
                Checks.NOT_REQUIRED),
            Arguments.of(
                notFullyConverted(),
                samplePartialWithdrawalMandate(aPartialWithdrawalMandateDetails),
                Checks.NOT_REQUIRED));

    var notTulevaClientScenarios =
        Stream.of(
            Arguments.of(notConverted(), thirdPillarMandate(), Checks.REQUIRED),
            Arguments.of(notConverted(), sampleMandate(), Checks.NOT_REQUIRED),
            Arguments.of(
                notConverted(),
                samplePartialWithdrawalMandate(aThirdPillarPartialWithdrawalMandateDetails),
                Checks.NOT_REQUIRED),
            Arguments.of(
                notConverted(),
                samplePartialWithdrawalMandate(aPartialWithdrawalMandateDetails),
                Checks.NOT_REQUIRED));

    return Stream.concat(tulevaClientScenarios, notTulevaClientScenarios);
  }

  @ParameterizedTest
  @MethodSource("amlChecksRequiredMandateScenarios")
  @DisplayName("isMandateAmlCheckRequired: works correctly")
  void isMandateAmlCheckRequired_returnsTrue(
      ConversionResponse conversion, Mandate mandate, Checks expectedResult) {
    User user = createUser("12345", "Test", "User", 1L);
    when(userConversionService.getConversion(user)).thenReturn(conversion);

    boolean actualResult = amlService.isMandateAmlCheckRequired(user, mandate);

    assertEquals(expectedResult.checksRequired, actualResult);
  }

  @Test
  @DisplayName(
      "addSanctionAndPepCheckIfMissing: adds PEP and Sanction checks based on match response")
  void addSanctionAndPepCheckIfMissing_addsChecks() {
    // given
    User user = createUser("123", "First", "Last", 1L);
    Country country = new Country("EE");
    ArrayNode results = objectMapper.createArrayNode();
    ObjectNode result1 = objectMapper.createObjectNode();
    result1.put("id", "res123");
    result1.put("match", true);
    ArrayNode topics = objectMapper.createArrayNode();
    topics.add("role.pep");
    ObjectNode properties = objectMapper.createObjectNode();
    properties.set("topics", topics);
    result1.set("properties", properties);
    results.add(result1);
    JsonNode query = objectMapper.createObjectNode();
    MatchResponse matchResponse = new MatchResponse(results, query);

    when(pepAndSanctionCheckService.match(user, country)).thenReturn(matchResponse);
    when(amlCheckRepository.existsByPersonalCodeAndTypeAndCreatedTimeAfter(
            anyString(), any(AmlCheckType.class), any(Instant.class)))
        .thenReturn(false);
    when(amlCheckRepository.findAllByPersonalCodeAndTypeAndSuccess(
            user.getPersonalCode(), POLITICALLY_EXPOSED_PERSON_OVERRIDE, true))
        .thenReturn(Collections.emptyList());
    when(amlCheckRepository.findAllByPersonalCodeAndTypeAndSuccess(
            user.getPersonalCode(), SANCTION_OVERRIDE, true))
        .thenReturn(Collections.emptyList());

    // when
    List<AmlCheck> addedChecks = amlService.addSanctionAndPepCheckIfMissing(user, country);

    // then
    verify(amlCheckRepository, times(2)).save(amlCheckCaptor.capture());
    List<AmlCheck> savedChecks = amlCheckCaptor.getAllValues();

    AmlCheck pepCheck =
        savedChecks.stream()
            .filter(c -> c.getType() == POLITICALLY_EXPOSED_PERSON_AUTO)
            .findFirst()
            .orElseThrow();
    AmlCheck sanctionCheck =
        savedChecks.stream().filter(c -> c.getType() == SANCTION).findFirst().orElseThrow();

    assertFalse(pepCheck.isSuccess(), "PEP check should fail due to match");
    assertTrue(sanctionCheck.isSuccess(), "Sanction check should pass as no sanction topic match");

    assertEquals(2, addedChecks.size());
    assertTrue(addedChecks.contains(pepCheck));
    assertTrue(addedChecks.contains(sanctionCheck));

    verify(eventPublisher, times(2)).publishEvent(any(AmlCheckCreatedEvent.class));
  }

  @Test
  @DisplayName("addSanctionAndPepCheckIfMissing: considers overrides for PEP and Sanction checks")
  void addSanctionAndPepCheckIfMissing_considersOverrides() {
    // given
    User user = createUser("123", "First", "Last", 1L);
    Country country = new Country("EE");

    ArrayNode resultsArray = objectMapper.createArrayNode();
    ObjectNode resultNode = JsonNodeFactory.instance.objectNode();
    resultNode.put("id", "matchId123");
    resultNode.put("match", true);
    ObjectNode propertiesNode = JsonNodeFactory.instance.objectNode();
    propertiesNode.set(
        "topics", JsonNodeFactory.instance.arrayNode().add("role.pep").add("sanction"));
    resultNode.set("properties", propertiesNode);
    resultsArray.add(resultNode);
    JsonNode queryNode = JsonNodeFactory.instance.objectNode();
    MatchResponse matchResponse = new MatchResponse(resultsArray, queryNode);

    when(pepAndSanctionCheckService.match(user, country)).thenReturn(matchResponse);
    when(amlCheckRepository.existsByPersonalCodeAndTypeAndCreatedTimeAfter(
            anyString(), any(AmlCheckType.class), any(Instant.class)))
        .thenReturn(false);

    AmlCheck pepOverride =
        AmlCheck.builder()
            .type(POLITICALLY_EXPOSED_PERSON_OVERRIDE)
            .success(true)
            .metadata(Map.of("results", List.of(Map.of("id", "matchId123"))))
            .build();
    when(amlCheckRepository.findAllByPersonalCodeAndTypeAndSuccess(
            user.getPersonalCode(), POLITICALLY_EXPOSED_PERSON_OVERRIDE, true))
        .thenReturn(List.of(pepOverride));

    when(amlCheckRepository.findAllByPersonalCodeAndTypeAndSuccess(
            user.getPersonalCode(), SANCTION_OVERRIDE, true))
        .thenReturn(Collections.emptyList());

    // when
    List<AmlCheck> addedChecks = amlService.addSanctionAndPepCheckIfMissing(user, country);

    // then
    verify(amlCheckRepository, times(2)).save(amlCheckCaptor.capture());
    List<AmlCheck> savedChecks = amlCheckCaptor.getAllValues();
    AmlCheck pepAutoCheck =
        savedChecks.stream()
            .filter(c -> c.getType() == POLITICALLY_EXPOSED_PERSON_AUTO)
            .findFirst()
            .orElseThrow();
    AmlCheck sanctionCheck =
        savedChecks.stream().filter(c -> c.getType() == SANCTION).findFirst().orElseThrow();

    assertTrue(pepAutoCheck.isSuccess(), "PEP check should be successful due to override");
    assertFalse(
        sanctionCheck.isSuccess(), "Sanction check should fail as there's a match and no override");
  }

  @Test
  @DisplayName("addSanctionAndPepCheckIfMissing: handles RuntimeException from match service")
  void addSanctionAndPepCheckIfMissing_handlesMatchServiceException() {
    // given
    User user = createUser("123", "First", "Last", 1L);
    Country country = new Country("EE");
    when(pepAndSanctionCheckService.match(user, country))
        .thenThrow(new RuntimeException("Match service error"));

    // when
    List<AmlCheck> result = amlService.addSanctionAndPepCheckIfMissing(user, country);

    // then
    assertTrue(result.isEmpty(), "Should return an empty list on exception");
    verify(amlCheckRepository, never()).save(any());
  }

  @Test
  @DisplayName("addSanctionAndPepCheckIfMissing: handles override check with null results in metadata")
  void addSanctionAndPepCheckIfMissing_handlesNullResultsInOverrideMetadata() {
    // given
    User user = createUser("123", "First", "Last", 1L);
    Country country = new Country("EE");

    ArrayNode resultsArray = objectMapper.createArrayNode();
    ObjectNode resultNode = JsonNodeFactory.instance.objectNode();
    resultNode.put("id", "matchId123");
    resultNode.put("match", true);
    ObjectNode propertiesNode = JsonNodeFactory.instance.objectNode();
    propertiesNode.set("topics", JsonNodeFactory.instance.arrayNode().add("role.pep"));
    resultNode.set("properties", propertiesNode);
    resultsArray.add(resultNode);
    JsonNode queryNode = JsonNodeFactory.instance.objectNode();
    MatchResponse matchResponse = new MatchResponse(resultsArray, queryNode);

    when(pepAndSanctionCheckService.match(user, country)).thenReturn(matchResponse);
    when(amlCheckRepository.existsByPersonalCodeAndTypeAndCreatedTimeAfter(
            anyString(), any(AmlCheckType.class), any(Instant.class)))
        .thenReturn(false);

    AmlCheck overrideWithNullResults =
        AmlCheck.builder()
            .type(POLITICALLY_EXPOSED_PERSON_OVERRIDE)
            .success(true)
            .metadata(Map.of("query", "some query"))
            .build();
    when(amlCheckRepository.findAllByPersonalCodeAndTypeAndSuccess(
            user.getPersonalCode(), POLITICALLY_EXPOSED_PERSON_OVERRIDE, true))
        .thenReturn(List.of(overrideWithNullResults));
    when(amlCheckRepository.findAllByPersonalCodeAndTypeAndSuccess(
            user.getPersonalCode(), SANCTION_OVERRIDE, true))
        .thenReturn(Collections.emptyList());

    // when
    List<AmlCheck> addedChecks = amlService.addSanctionAndPepCheckIfMissing(user, country);

    // then
    verify(amlCheckRepository, times(2)).save(amlCheckCaptor.capture());
    List<AmlCheck> savedChecks = amlCheckCaptor.getAllValues();
    AmlCheck pepAutoCheck =
        savedChecks.stream()
            .filter(c -> c.getType() == POLITICALLY_EXPOSED_PERSON_AUTO)
            .findFirst()
            .orElseThrow();

    assertFalse(
        pepAutoCheck.isSuccess(),
        "PEP check should fail as override has null results and cannot match");
    assertEquals(2, addedChecks.size());
  }

  @Test
  @DisplayName("runAmlChecksOnThirdPillarCustomers: processes records and adds checks")
  void runAmlChecksOnThirdPillarCustomers_processesRecords() {
    // given
    AnalyticsRecentThirdPillar record1 = mock(AnalyticsRecentThirdPillar.class);
    when(record1.getPersonalCode()).thenReturn("p1");
    when(record1.getCountry()).thenReturn("EE");
    when(record1.getFirstName()).thenReturn("F1");
    when(record1.getLastName()).thenReturn("L1");

    List<AnalyticsRecentThirdPillar> records = List.of(record1);
    when(analyticsRecentThirdPillarRepository.findAll()).thenReturn(records);

    MatchResponse emptyMatchResponse =
        new MatchResponse(objectMapper.createArrayNode(), objectMapper.createObjectNode());
    when(pepAndSanctionCheckService.match(eq(record1), any(Country.class)))
        .thenReturn(emptyMatchResponse);
    when(amlCheckRepository.existsByPersonalCodeAndTypeAndCreatedTimeAfter(
            anyString(), any(AmlCheckType.class), any(Instant.class)))
        .thenReturn(false);

    when(amlCheckRepository.findAllByPersonalCodeAndTypeAndSuccess(
            anyString(), eq(POLITICALLY_EXPOSED_PERSON_OVERRIDE), eq(true)))
        .thenReturn(Collections.emptyList());
    when(amlCheckRepository.findAllByPersonalCodeAndTypeAndSuccess(
            anyString(), eq(SANCTION_OVERRIDE), eq(true)))
        .thenReturn(Collections.emptyList());

    // when
    amlService.runAmlChecksOnThirdPillarCustomers();

    // then
    verify(eventPublisher).publishEvent(any(AmlChecksRunEvent.class));

    verify(amlCheckRepository, times(2)).save(any(AmlCheck.class));
    verify(eventPublisher, times(2)).publishEvent(any(AmlCheckCreatedEvent.class));
  }

  private static List<AmlCheck> successfulChecks(AmlCheckType... checkTypes) {
    return Arrays.stream(checkTypes)
        .map(type -> AmlCheck.builder().type(type).success(true).build())
        .collect(Collectors.toList());
  }

  private static List<AmlCheck> failedChecks(AmlCheckType... checkTypes) {
    return Arrays.stream(checkTypes)
        .map(type -> AmlCheck.builder().type(type).success(false).build())
        .collect(Collectors.toList());
  }
}
