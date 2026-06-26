package ee.tuleva.onboarding.aml;

import static ee.tuleva.onboarding.aml.AmlCheckType.*;
import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.conversion.ConversionResponseFixture.notFullyConverted;
import static ee.tuleva.onboarding.mandate.MandateFixture.sampleMandate;
import static ee.tuleva.onboarding.mandate.MandateFixture.thirdPillarMandate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.aml.sanctions.MatchResponse;
import ee.tuleva.onboarding.aml.sanctions.PepAndSanctionCheckService;
import ee.tuleva.onboarding.conversion.UserConversionService;
import ee.tuleva.onboarding.country.Country;
import ee.tuleva.onboarding.user.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@SpringBootTest
public class AmlServiceIntegrationTest {

  @TestConfiguration
  static class AmlServiceTestConfiguration {
    @Bean
    @Primary
    public PepAndSanctionCheckService pepAndSanctionCheckService() {
      return mock(PepAndSanctionCheckService.class);
    }

    @Bean
    @Primary
    public UserConversionService userConversionService() {
      return mock(UserConversionService.class);
    }
  }

  @Autowired private AmlService amlService;
  @Autowired private AmlCheckRepository amlCheckRepository;
  @Autowired private PepAndSanctionCheckService checkService;
  @Autowired private UserConversionService userConversionService;
  @PersistenceContext private EntityManager entityManager;

  @BeforeEach
  void setUp() {
    // given
    MatchResponse mockResponse = mock(MatchResponse.class);
    JsonMapper objectMapper = JsonMapper.builder().build();

    ArrayNode emptyArrayNode = objectMapper.createArrayNode();
    when(mockResponse.results()).thenReturn(emptyArrayNode);

    ObjectNode emptyQueryNode = objectMapper.createObjectNode();
    when(mockResponse.query()).thenReturn(emptyQueryNode);

    when(checkService.match(any(), any())).thenReturn(mockResponse);
    when(userConversionService.getConversion(any())).thenReturn(notFullyConverted());
  }

  @Test
  @Transactional
  void shouldAddDocumentCheckForUser() {
    // given
    User user = sampleUser().build();

    // when
    amlService.checkUserBeforeLogin(user, user, true);

    // then
    List<AmlCheck> checks =
        amlCheckRepository.findAllByPersonalCodeAndCreatedTimeAfter(
            user.getPersonalCode(), Instant.now().minusSeconds(3600));
    assertThat(checks).hasSize(3);
    assertThat(checks).anyMatch(check -> check.getType() == DOCUMENT && check.isSuccess());
    assertThat(checks).anyMatch(check -> check.getType() == SK_NAME && check.isSuccess());
    assertThat(checks).anyMatch(check -> check.getType() == RESIDENCY_AUTO && check.isSuccess());
  }

  @Test
  @Transactional
  void doesResidencyCheck() {
    // given
    User user = sampleUser().build();

    // when
    amlService.checkUserBeforeLogin(user, user, true);

    // then
    List<AmlCheck> checks =
        amlCheckRepository.findAllByPersonalCodeAndCreatedTimeAfter(
            user.getPersonalCode(), Instant.now().minusSeconds(3600));
    assertThat(checks).hasSize(3);
    assertThat(checks).anyMatch(check -> check.getType() == DOCUMENT && check.isSuccess());
    assertThat(checks).anyMatch(check -> check.getType() == SK_NAME && check.isSuccess());
    assertThat(checks).anyMatch(check -> check.getType() == RESIDENCY_AUTO && check.isSuccess());
  }

  @Test
  @Transactional
  void shouldAddPepAndSanctionChecks() {
    // given
    User user = sampleUser().build();
    Country country = new Country("EE");

    // when
    List<AmlCheck> checks = amlService.addSanctionAndPepCheckIfMissing(user, country);

    // then
    assertThat(checks).hasSize(2);
    assertThat(checks).anyMatch(check -> check.getType() == POLITICALLY_EXPOSED_PERSON_AUTO);
    assertThat(checks).anyMatch(check -> check.getType() == SANCTION);
  }

  @Test
  @Transactional
  void pepMatchResultsArePersistedAsArrayNotBeanSerializedBlob() {
    User user = sampleUser().build();
    Country country = new Country("EE");

    JsonMapper objectMapper = JsonMapper.builder().build();
    ArrayNode results = objectMapper.createArrayNode();
    ObjectNode result = objectMapper.createObjectNode();
    result.put("id", "match-1");
    result.put("match", false);
    ObjectNode properties = objectMapper.createObjectNode();
    properties.set("topics", objectMapper.createArrayNode().add("role.pep"));
    result.set("properties", properties);
    results.add(result);
    ObjectNode query = objectMapper.createObjectNode();
    query.put("schema", "Person");

    MatchResponse matchResponse = mock(MatchResponse.class);
    when(matchResponse.results()).thenReturn(results);
    when(matchResponse.query()).thenReturn(query);
    when(checkService.match(any(), any())).thenReturn(matchResponse);

    amlService.addSanctionAndPepCheckIfMissing(user, country);
    entityManager.flush();
    entityManager.clear();

    AmlCheck persisted =
        amlCheckRepository
            .findAllByPersonalCodeAndCreatedTimeAfter(
                user.getPersonalCode(), Instant.now().minusSeconds(3600))
            .stream()
            .filter(check -> check.getType() == POLITICALLY_EXPOSED_PERSON_AUTO)
            .findFirst()
            .orElseThrow();

    Object persistedResults = persisted.getMetadata().get("results");
    assertThat(persistedResults).isInstanceOf(List.class);
    assertThat((List<?>) persistedResults).hasSize(1);
    assertThat((Map<String, Object>) ((List<?>) persistedResults).getFirst())
        .containsEntry("id", "match-1")
        .doesNotContainKey("nodeType");

    Object persistedQuery = persisted.getMetadata().get("query");
    assertThat(persistedQuery).isInstanceOf(Map.class);
    assertThat((Map<String, Object>) persistedQuery)
        .containsEntry("schema", "Person")
        .doesNotContainKey("nodeType");
  }

  @Test
  @Transactional
  void shouldVerifyAllChecksPassForThirdPillar() {
    // given
    User user = sampleUser().build();
    Country country = new Country("EE");

    amlService.checkUserBeforeLogin(user, user, true);
    amlService.addSanctionAndPepCheckIfMissing(user, country);

    AmlCheck occupationCheck =
        AmlCheck.builder()
            .personalCode(user.getPersonalCode())
            .type(OCCUPATION)
            .success(true)
            .build();
    amlCheckRepository.save(occupationCheck);

    var mandate = thirdPillarMandate();
    assertEquals(3, mandate.getPillar());

    // when
    boolean allPassed = amlService.allChecksPassed(user, mandate);

    // then
    assertThat(allPassed).isTrue();
  }

  @Test
  @Transactional
  void shouldFailThirdPillarWhenMissingChecks() {
    // given
    User user = sampleUser().build();
    amlService.checkUserBeforeLogin(user, user, true);
    var mandate = thirdPillarMandate();
    assertEquals(3, mandate.getPillar());

    // when
    boolean allPassed = amlService.allChecksPassed(user, mandate);

    // then
    assertThat(allPassed).isFalse();
  }

  @Test
  @Transactional
  void shouldFailWhenSanctionMatchIsFound() {
    // given
    User user = sampleUser().build();
    Country country = new Country("EE");

    JsonMapper objectMapper = JsonMapper.builder().build();
    ArrayNode results = objectMapper.createArrayNode();
    ObjectNode result = objectMapper.createObjectNode();
    result.put("match", true);

    ObjectNode properties = objectMapper.createObjectNode();
    ArrayNode topics = objectMapper.createArrayNode();
    topics.add("sanction"); // Topic that triggers a sanction match
    properties.set("topics", topics);
    result.set("properties", properties);
    results.add(result);

    ObjectNode query = objectMapper.createObjectNode();

    MatchResponse sanctionMatchResponse = mock(MatchResponse.class);
    when(sanctionMatchResponse.results()).thenReturn(results);
    when(sanctionMatchResponse.query()).thenReturn(query);
    when(checkService.match(any(), any())).thenReturn(sanctionMatchResponse);

    // when
    List<AmlCheck> checks = amlService.addSanctionAndPepCheckIfMissing(user, country);

    // then
    assertThat(checks).anyMatch(check -> check.getType() == SANCTION && !check.isSuccess());
  }

  @Test
  @Transactional
  void shouldNotAddResidencyCheckWhenNull() {
    // given
    User user = sampleUser().build();

    // when
    amlService.checkUserBeforeLogin(user, user, null);

    // then
    List<AmlCheck> checks =
        amlCheckRepository.findAllByPersonalCodeAndCreatedTimeAfter(
            user.getPersonalCode(), Instant.now().minusSeconds(3600));
    assertThat(checks).anyMatch(check -> check.getType() == DOCUMENT);
    assertThat(checks).anyMatch(check -> check.getType() == SK_NAME);
    assertThat(checks).noneMatch(check -> check.getType() == RESIDENCY_AUTO);
  }

  @Test
  @Transactional
  void shouldAlwaysPassForSecondPillar() {
    // given
    User user = sampleUser().build();
    var mandate = sampleMandate();
    assertEquals(2, mandate.getPillar());

    // when
    boolean allPassed = amlService.allChecksPassed(user, mandate);

    // then
    assertThat(allPassed).isTrue();
  }

  @Test
  @Transactional
  void shouldFailNameCheckWhenNamesDontMatch() {
    // given
    User user = sampleUser().firstName("John").lastName("Doe").build();
    User differingPerson = sampleUser().firstName("Jane").lastName("Dough").build();

    // when
    amlService.checkUserBeforeLogin(user, differingPerson, true);

    // then
    List<AmlCheck> checks =
        amlCheckRepository.findAllByPersonalCodeAndCreatedTimeAfter(
            user.getPersonalCode(), Instant.now().minusSeconds(3600));
    assertThat(checks).anyMatch(check -> check.getType() == SK_NAME && !check.isSuccess());
  }
}
