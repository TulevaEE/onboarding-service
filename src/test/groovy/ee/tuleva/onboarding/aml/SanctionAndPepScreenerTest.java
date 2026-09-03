package ee.tuleva.onboarding.aml;

import static ee.tuleva.onboarding.aml.AmlCheckType.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.mockito.quality.Strictness.LENIENT;

import ee.tuleva.onboarding.aml.notification.AmlCheckCreatedEvent;
import ee.tuleva.onboarding.aml.sanctions.MatchResponse;
import ee.tuleva.onboarding.aml.sanctions.PepAndSanctionCheckService;
import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.auth.principal.PersonImpl;
import ee.tuleva.onboarding.conversion.UserConversionService;
import ee.tuleva.onboarding.country.Countries;
import ee.tuleva.onboarding.country.Country;
import ee.tuleva.onboarding.kyc.KycCountryService;
import ee.tuleva.onboarding.user.User;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.context.ApplicationEventPublisher;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

@MockitoSettings(strictness = LENIENT)
@ExtendWith(MockitoExtension.class)
class SanctionAndPepScreenerTest {

  @Mock private AmlCheckRepository amlCheckRepository;
  @Mock private ApplicationEventPublisher eventPublisher;
  @Mock private PepAndSanctionCheckService pepAndSanctionCheckService;
  @Mock private KycCountryService kycCountryService;
  @Spy private JsonMapper jsonMapper = JsonMapper.builder().build();
  @Spy private MeterRegistry meterRegistry = new SimpleMeterRegistry();

  private SanctionAndPepScreener sanctionAndPepScreener;

  @Captor private ArgumentCaptor<AmlCheck> amlCheckCaptor;

  private static final JsonMapper objectMapper = JsonMapper.builder().build();

  @BeforeEach
  void setUp() {
    AmlService amlService =
        new AmlService(amlCheckRepository, eventPublisher, mock(UserConversionService.class));
    sanctionAndPepScreener =
        new SanctionAndPepScreener(
            amlCheckRepository,
            pepAndSanctionCheckService,
            kycCountryService,
            jsonMapper,
            meterRegistry,
            amlService);
    lenient()
        .when(amlCheckRepository.save(any(AmlCheck.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  private User createUser(String personalCode, String firstName, String lastName, Long id) {
    User user = mock(User.class);
    when(user.getPersonalCode()).thenReturn(personalCode);
    when(user.getFirstName()).thenReturn(firstName);
    when(user.getLastName()).thenReturn(lastName);
    when(user.getId()).thenReturn(id);
    return user;
  }

  private User savingsFundCustomer(String personalCode, Long id) {
    return User.builder()
        .id(id)
        .personalCode(personalCode)
        .firstName("First" + personalCode)
        .lastName("Last" + personalCode)
        .build();
  }

  private void givenRecordedCitizenships(String personalCode, List<String> citizenships) {
    given(
            amlCheckRepository.findFirstByPersonalCodeAndTypeOrderByCreatedTimeDescIdDesc(
                personalCode, CUSTODY_RIGHT))
        .willReturn(
            Optional.of(
                AmlCheck.builder()
                    .personalCode(personalCode)
                    .type(CUSTODY_RIGHT)
                    .success(true)
                    .metadata(Map.of("citizenships", citizenships))
                    .build()));
  }

  private void latestCheckIs(AmlCheckType type, boolean success) {
    when(amlCheckRepository.findFirstByPersonalCodeAndTypeOrderByCreatedTimeDescIdDesc(
            anyString(), eq(type)))
        .thenReturn(
            Optional.of(
                AmlCheck.builder()
                    .personalCode("38812121215")
                    .type(type)
                    .success(success)
                    .build()));
  }

  private MatchResponse sanctionAndPepMatch() {
    ArrayNode results = objectMapper.createArrayNode();
    ObjectNode result = JsonNodeFactory.instance.objectNode();
    result.put("id", "matchId123");
    result.put("match", true);
    ObjectNode properties = JsonNodeFactory.instance.objectNode();
    properties.set("topics", JsonNodeFactory.instance.arrayNode().add("role.pep").add("sanction"));
    result.set("properties", properties);
    results.add(result);
    return new MatchResponse(results, JsonNodeFactory.instance.objectNode());
  }

  @Test
  void addSanctionAndPepCheckIfMissing_addsChecks() {
    // given
    User user = createUser("123", "First", "Last", 1L);
    Set<Country> country = Countries.of("EE");
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
        .thenReturn(List.of());
    when(amlCheckRepository.findAllByPersonalCodeAndTypeAndSuccess(
            user.getPersonalCode(), SANCTION_OVERRIDE, true))
        .thenReturn(List.of());

    // when
    List<AmlCheck> addedChecks =
        sanctionAndPepScreener.addSanctionAndPepCheckIfMissing(user, country);

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
  void addSanctionAndPepCheckIfMissing_considersOverrides() {
    // given
    User user = createUser("123", "First", "Last", 1L);
    Set<Country> country = Countries.of("EE");

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
        .thenReturn(List.of());

    // when
    List<AmlCheck> addedChecks =
        sanctionAndPepScreener.addSanctionAndPepCheckIfMissing(user, country);

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
  void blanketOverrideWithoutResultsMetadata_appliesToAllMatches() {
    // given
    User user = createUser("123", "First", "Last", 1L);
    Set<Country> country = Countries.of("EE");

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

    AmlCheck blanketOverrideWithoutResults =
        AmlCheck.builder()
            .type(POLITICALLY_EXPOSED_PERSON_OVERRIDE)
            .success(true)
            .metadata(Map.of("comment", "Kinnitan ärisuhte", "createdBy", "admin"))
            .build();
    when(amlCheckRepository.findAllByPersonalCodeAndTypeAndSuccess(
            user.getPersonalCode(), POLITICALLY_EXPOSED_PERSON_OVERRIDE, true))
        .thenReturn(List.of(blanketOverrideWithoutResults));
    when(amlCheckRepository.findAllByPersonalCodeAndTypeAndSuccess(
            user.getPersonalCode(), SANCTION_OVERRIDE, true))
        .thenReturn(List.of());

    // when
    sanctionAndPepScreener.addSanctionAndPepCheckIfMissing(user, country);

    // then
    verify(amlCheckRepository, times(2)).save(amlCheckCaptor.capture());
    List<AmlCheck> savedChecks = amlCheckCaptor.getAllValues();
    AmlCheck pepAutoCheck =
        savedChecks.stream()
            .filter(c -> c.getType() == POLITICALLY_EXPOSED_PERSON_AUTO)
            .findFirst()
            .orElseThrow();

    assertThat(pepAutoCheck.isSuccess()).isTrue();
  }

  @Test
  void overrideWithObjectShapedResultsMetadata_doesNotCrashAndAppliesOverride() {
    User user = createUser("123", "First", "Last", 1L);
    Set<Country> country = Countries.of("EE");

    ArrayNode resultsArray = objectMapper.createArrayNode();
    ObjectNode resultNode = JsonNodeFactory.instance.objectNode();
    resultNode.put("id", "matchId123");
    resultNode.put("match", true);
    ObjectNode propertiesNode = JsonNodeFactory.instance.objectNode();
    propertiesNode.set("topics", JsonNodeFactory.instance.arrayNode().add("role.pep"));
    resultNode.set("properties", propertiesNode);
    resultsArray.add(resultNode);
    MatchResponse matchResponse =
        new MatchResponse(resultsArray, JsonNodeFactory.instance.objectNode());

    when(pepAndSanctionCheckService.match(user, country)).thenReturn(matchResponse);
    when(amlCheckRepository.existsByPersonalCodeAndTypeAndCreatedTimeAfter(
            anyString(), any(AmlCheckType.class), any(Instant.class)))
        .thenReturn(false);

    AmlCheck corruptOverride =
        AmlCheck.builder()
            .type(POLITICALLY_EXPOSED_PERSON_OVERRIDE)
            .success(true)
            .metadata(Map.of("results", Map.of("nodeType", "ARRAY", "array", true)))
            .build();
    when(amlCheckRepository.findAllByPersonalCodeAndTypeAndSuccess(
            user.getPersonalCode(), POLITICALLY_EXPOSED_PERSON_OVERRIDE, true))
        .thenReturn(List.of(corruptOverride));
    when(amlCheckRepository.findAllByPersonalCodeAndTypeAndSuccess(
            user.getPersonalCode(), SANCTION_OVERRIDE, true))
        .thenReturn(List.of());

    List<AmlCheck> addedChecks =
        sanctionAndPepScreener.addSanctionAndPepCheckIfMissing(user, country);

    AmlCheck pepAutoCheck =
        addedChecks.stream()
            .filter(check -> check.getType() == POLITICALLY_EXPOSED_PERSON_AUTO)
            .findFirst()
            .orElseThrow();
    assertThat(pepAutoCheck.isSuccess()).isTrue();
  }

  @Test
  void overrideWithNonMatchingResults_doesNotApplyAndCheckFails() {
    User user = createUser("123", "First", "Last", 1L);
    Set<Country> country = Countries.of("EE");

    ArrayNode resultsArray = objectMapper.createArrayNode();
    ObjectNode resultNode = JsonNodeFactory.instance.objectNode();
    resultNode.put("id", "currentMatch");
    resultNode.put("match", true);
    ObjectNode propertiesNode = JsonNodeFactory.instance.objectNode();
    propertiesNode.set("topics", JsonNodeFactory.instance.arrayNode().add("role.pep"));
    resultNode.set("properties", propertiesNode);
    resultsArray.add(resultNode);
    MatchResponse matchResponse =
        new MatchResponse(resultsArray, JsonNodeFactory.instance.objectNode());

    when(pepAndSanctionCheckService.match(user, country)).thenReturn(matchResponse);
    when(amlCheckRepository.existsByPersonalCodeAndTypeAndCreatedTimeAfter(
            anyString(), any(AmlCheckType.class), any(Instant.class)))
        .thenReturn(false);

    AmlCheck nonMatchingOverride =
        AmlCheck.builder()
            .type(POLITICALLY_EXPOSED_PERSON_OVERRIDE)
            .success(true)
            .metadata(Map.of("results", List.of("not-a-map", Map.of("id", "otherMatch"))))
            .build();
    when(amlCheckRepository.findAllByPersonalCodeAndTypeAndSuccess(
            user.getPersonalCode(), POLITICALLY_EXPOSED_PERSON_OVERRIDE, true))
        .thenReturn(List.of(nonMatchingOverride));
    when(amlCheckRepository.findAllByPersonalCodeAndTypeAndSuccess(
            user.getPersonalCode(), SANCTION_OVERRIDE, true))
        .thenReturn(List.of());

    List<AmlCheck> addedChecks =
        sanctionAndPepScreener.addSanctionAndPepCheckIfMissing(user, country);

    AmlCheck pepAutoCheck =
        addedChecks.stream()
            .filter(check -> check.getType() == POLITICALLY_EXPOSED_PERSON_AUTO)
            .findFirst()
            .orElseThrow();
    assertThat(pepAutoCheck.isSuccess()).isFalse();
  }

  @Test
  void addSanctionAndPepCheckIfMissing_handlesMatchServiceException() {
    // given
    User user = createUser("123", "First", "Last", 1L);
    Set<Country> country = Countries.of("EE");
    when(pepAndSanctionCheckService.match(user, country))
        .thenThrow(new RuntimeException("Match service error"));

    // when
    List<AmlCheck> result = sanctionAndPepScreener.addSanctionAndPepCheckIfMissing(user, country);

    // then
    assertTrue(result.isEmpty(), "Should return an empty list on exception");
    verify(amlCheckRepository, never()).save(any());
  }

  @Test
  void addSanctionAndPepCheckIfMissing_screeningFailureIsObservable() {
    // given
    User user = createUser("38001010000", "First", "Last", 1L);
    Set<Country> country = Countries.of("EE");
    when(pepAndSanctionCheckService.match(user, country))
        .thenThrow(new RuntimeException("Match service error"));

    // when
    List<AmlCheck> result = sanctionAndPepScreener.addSanctionAndPepCheckIfMissing(user, country);

    // then
    assertTrue(result.isEmpty(), "Should return an empty list on exception");
    assertEquals(
        1.0,
        meterRegistry.counter("aml.screening.failure", "phase", "match").count(),
        "Screening failure should increment the aml.screening.failure metric");
  }

  @Test
  void screening_recordsNewlyFailingResultDespiteARecentPassingCheck() {
    User user = createUser("38812121215", "Test", "User", 1L);
    when(pepAndSanctionCheckService.match(eq(user), anySet())).thenReturn(sanctionAndPepMatch());
    when(amlCheckRepository.existsByPersonalCodeAndTypeAndCreatedTimeAfter(
            anyString(), any(AmlCheckType.class), any(Instant.class)))
        .thenReturn(true);
    latestCheckIs(SANCTION, true);
    latestCheckIs(POLITICALLY_EXPOSED_PERSON_AUTO, true);

    sanctionAndPepScreener.addSanctionAndPepCheckIfMissing(user, Countries.of("EE"));

    verify(amlCheckRepository, times(2)).save(amlCheckCaptor.capture());
    assertThat(amlCheckCaptor.getAllValues())
        .extracting(AmlCheck::getType, AmlCheck::isSuccess)
        .containsExactlyInAnyOrder(
            tuple(POLITICALLY_EXPOSED_PERSON_AUTO, false), tuple(SANCTION, false));
  }

  @Test
  void screening_recordsRecoveryWhenAPreviouslyFailingPersonNoLongerMatches() {
    User user = createUser("38812121215", "Test", "User", 1L);
    when(pepAndSanctionCheckService.match(eq(user), anySet()))
        .thenReturn(
            new MatchResponse(objectMapper.createArrayNode(), objectMapper.createObjectNode()));
    when(amlCheckRepository.existsByPersonalCodeAndTypeAndCreatedTimeAfter(
            anyString(), any(AmlCheckType.class), any(Instant.class)))
        .thenReturn(true);
    latestCheckIs(SANCTION, false);
    latestCheckIs(POLITICALLY_EXPOSED_PERSON_AUTO, false);

    sanctionAndPepScreener.addSanctionAndPepCheckIfMissing(user, Countries.of("EE"));

    verify(amlCheckRepository, times(2)).save(amlCheckCaptor.capture());
    assertThat(amlCheckCaptor.getAllValues())
        .extracting(AmlCheck::getType, AmlCheck::isSuccess)
        .containsExactlyInAnyOrder(
            tuple(POLITICALLY_EXPOSED_PERSON_AUTO, true), tuple(SANCTION, true));
  }

  @Test
  void screening_doesNotRecordAnUnchangedOutcomeWithinTheWindow() {
    User user = createUser("38812121215", "Test", "User", 1L);
    when(pepAndSanctionCheckService.match(eq(user), anySet()))
        .thenReturn(
            new MatchResponse(objectMapper.createArrayNode(), objectMapper.createObjectNode()));
    when(amlCheckRepository.existsByPersonalCodeAndTypeAndCreatedTimeAfter(
            anyString(), any(AmlCheckType.class), any(Instant.class)))
        .thenReturn(true);
    latestCheckIs(SANCTION, true);
    latestCheckIs(POLITICALLY_EXPOSED_PERSON_AUTO, true);

    sanctionAndPepScreener.addSanctionAndPepCheckIfMissing(user, Countries.of("EE"));

    verify(amlCheckRepository, never()).save(any(AmlCheck.class));
  }

  @Test
  void screening_recordsAnUnchangedOutcomeOnceTheWindowHasExpired() {
    User user = createUser("38812121215", "Test", "User", 1L);
    when(pepAndSanctionCheckService.match(eq(user), anySet()))
        .thenReturn(
            new MatchResponse(objectMapper.createArrayNode(), objectMapper.createObjectNode()));
    when(amlCheckRepository.existsByPersonalCodeAndTypeAndCreatedTimeAfter(
            anyString(), any(AmlCheckType.class), any(Instant.class)))
        .thenReturn(false);
    latestCheckIs(SANCTION, true);
    latestCheckIs(POLITICALLY_EXPOSED_PERSON_AUTO, true);

    sanctionAndPepScreener.addSanctionAndPepCheckIfMissing(user, Countries.of("EE"));

    verify(amlCheckRepository, times(2)).save(any(AmlCheck.class));
  }

  @Test
  void addSanctionAndPepCheckIfMissing_screensOnKycSurveyCountriesAndRecordedCitizenships() {
    User user = savingsFundCustomer("38812121215", 3L);
    given(kycCountryService.getCountries(3L)).willReturn(Optional.of(Countries.of("EE", "RU")));
    givenRecordedCitizenships(user.getPersonalCode(), List.of("UA"));
    given(pepAndSanctionCheckService.match(any(Person.class), anySet()))
        .willReturn(
            new MatchResponse(objectMapper.createArrayNode(), objectMapper.createObjectNode()));

    sanctionAndPepScreener.addSanctionAndPepCheckIfMissing(user);

    verify(pepAndSanctionCheckService).match(user, Countries.of("EE", "RU", "UA"));
  }

  @Test
  void addSanctionAndPepCheckIfMissing_screensOnEstoniaOnlyWhenNothingIsKnownAboutTheUser() {
    User user = savingsFundCustomer("38812121215", 4L);
    given(kycCountryService.getCountries(4L)).willReturn(Optional.empty());
    given(pepAndSanctionCheckService.match(any(Person.class), anySet()))
        .willReturn(
            new MatchResponse(objectMapper.createArrayNode(), objectMapper.createObjectNode()));

    sanctionAndPepScreener.addSanctionAndPepCheckIfMissing(user);

    verify(pepAndSanctionCheckService).match(user, Countries.<String>of());
  }

  @Test
  void isSanctionAndPepClear_failsClosedWhenScreeningThrows() {
    User user = createUser("123", "First", "Last", 1L);
    Set<Country> country = Countries.of("EE");
    when(pepAndSanctionCheckService.match(user, country))
        .thenThrow(new RuntimeException("screening service down"));

    assertFalse(sanctionAndPepScreener.isSanctionAndPepClear(user, country));
  }

  @Test
  void isSanctionAndPepClear_trueWhenLatestScreeningChecksPass() {
    User user = createUser("123", "First", "Last", 1L);
    Set<Country> country = Countries.of("EE");
    MatchResponse emptyResponse =
        new MatchResponse(objectMapper.createArrayNode(), objectMapper.createObjectNode());
    when(pepAndSanctionCheckService.match(user, country)).thenReturn(emptyResponse);
    when(amlCheckRepository.findFirstByPersonalCodeAndTypeOrderByCreatedTimeDescIdDesc(
            "123", SANCTION))
        .thenReturn(
            Optional.of(
                AmlCheck.builder().personalCode("123").type(SANCTION).success(true).build()));
    when(amlCheckRepository.findFirstByPersonalCodeAndTypeOrderByCreatedTimeDescIdDesc(
            "123", POLITICALLY_EXPOSED_PERSON_AUTO))
        .thenReturn(
            Optional.of(
                AmlCheck.builder()
                    .personalCode("123")
                    .type(POLITICALLY_EXPOSED_PERSON_AUTO)
                    .success(true)
                    .build()));

    assertTrue(sanctionAndPepScreener.isSanctionAndPepClear(user, country));
  }

  @Test
  void isSanctionAndPepClear_falseWhenLatestSanctionCheckHasFailed() {
    User user = createUser("123", "First", "Last", 1L);
    Set<Country> country = Countries.of("EE");
    ArrayNode results = objectMapper.createArrayNode();
    ObjectNode result = objectMapper.createObjectNode();
    result.put("id", "sanction123");
    result.put("match", true);
    ArrayNode topics = objectMapper.createArrayNode();
    topics.add("sanction");
    ObjectNode properties = objectMapper.createObjectNode();
    properties.set("topics", topics);
    result.set("properties", properties);
    results.add(result);
    MatchResponse matchResponse = new MatchResponse(results, objectMapper.createObjectNode());
    when(pepAndSanctionCheckService.match(user, country)).thenReturn(matchResponse);
    when(amlCheckRepository.findFirstByPersonalCodeAndTypeOrderByCreatedTimeDescIdDesc(
            "123", SANCTION))
        .thenReturn(
            Optional.of(
                AmlCheck.builder().personalCode("123").type(SANCTION).success(false).build()));

    assertFalse(sanctionAndPepScreener.isSanctionAndPepClear(user, country));
  }

  @Test
  void isSanctionAndPepClear_falseWhenNoScreeningRecordExists() {
    User user = createUser("123", "First", "Last", 1L);
    Set<Country> country = Countries.of("EE");
    MatchResponse emptyResponse =
        new MatchResponse(objectMapper.createArrayNode(), objectMapper.createObjectNode());
    when(pepAndSanctionCheckService.match(user, country)).thenReturn(emptyResponse);
    when(amlCheckRepository.findFirstByPersonalCodeAndTypeOrderByCreatedTimeDescIdDesc(
            eq("123"), any(AmlCheckType.class)))
        .thenReturn(Optional.empty());

    assertFalse(sanctionAndPepScreener.isSanctionAndPepClear(user, country));
  }

  @Test
  void recordedCitizenships_readsEveryCitizenshipFromTheLatestCustodyCheck() {
    var person = new PersonImpl("61506150006", "Mari", "Maasikas");
    given(
            amlCheckRepository.findFirstByPersonalCodeAndTypeOrderByCreatedTimeDescIdDesc(
                person.getPersonalCode(), CUSTODY_RIGHT))
        .willReturn(
            Optional.of(
                AmlCheck.builder()
                    .personalCode(person.getPersonalCode())
                    .type(CUSTODY_RIGHT)
                    .success(true)
                    .metadata(Map.of("citizenship", "EE", "citizenships", List.of("EE", "RU")))
                    .build()));

    assertThat(sanctionAndPepScreener.recordedCitizenships(person))
        .isEqualTo(Countries.of("EE", "RU"));
  }

  @Test
  void recordedCitizenships_fallsBackToTheSingleCitizenshipOlderChecksRecorded() {
    var person = new PersonImpl("61506150006", "Mari", "Maasikas");
    given(
            amlCheckRepository.findFirstByPersonalCodeAndTypeOrderByCreatedTimeDescIdDesc(
                person.getPersonalCode(), CUSTODY_RIGHT))
        .willReturn(
            Optional.of(
                AmlCheck.builder()
                    .personalCode(person.getPersonalCode())
                    .type(CUSTODY_RIGHT)
                    .success(true)
                    .metadata(Map.of("citizenship", "EE"))
                    .build()));

    assertThat(sanctionAndPepScreener.recordedCitizenships(person)).isEqualTo(Countries.of("EE"));
  }

  @Test
  void recordedCitizenships_isEmptyWhenTheCustodyCheckRecordedNoCitizenship() {
    var person = new PersonImpl("61506150006", "Mari", "Maasikas");
    given(
            amlCheckRepository.findFirstByPersonalCodeAndTypeOrderByCreatedTimeDescIdDesc(
                person.getPersonalCode(), CUSTODY_RIGHT))
        .willReturn(
            Optional.of(
                AmlCheck.builder()
                    .personalCode(person.getPersonalCode())
                    .type(CUSTODY_RIGHT)
                    .success(false)
                    .metadata(Map.of("outcome", "NO_CUSTODY"))
                    .build()));

    assertThat(sanctionAndPepScreener.recordedCitizenships(person)).isEmpty();
  }

  @Test
  void recordedCitizenships_isEmptyWhenThereIsNoCustodyCheck() {
    var person = new PersonImpl("38812121215", "Jaan", "Tamm");
    given(
            amlCheckRepository.findFirstByPersonalCodeAndTypeOrderByCreatedTimeDescIdDesc(
                person.getPersonalCode(), CUSTODY_RIGHT))
        .willReturn(Optional.empty());

    assertThat(sanctionAndPepScreener.recordedCitizenships(person)).isEmpty();
  }
}
