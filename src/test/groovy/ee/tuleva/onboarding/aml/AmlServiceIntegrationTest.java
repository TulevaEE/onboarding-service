package ee.tuleva.onboarding.aml;

import static ee.tuleva.onboarding.aml.AmlCheckType.*;
import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ee.tuleva.onboarding.aml.sanctions.MatchResponse;
import ee.tuleva.onboarding.aml.sanctions.PepAndSanctionCheckService;
import ee.tuleva.onboarding.analytics.thirdpillar.AnalyticsThirdPillarRepository;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.address.Address;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class AmlServiceIntegrationTest {
  @Autowired private AmlCheckRepository amlCheckRepository;
  @Autowired private ApplicationEventPublisher eventPublisher;
  @Autowired private AnalyticsThirdPillarRepository analyticsThirdPillarRepository;
  private AmlService amlService;
  @MockBean private PepAndSanctionCheckService checkService;

  @BeforeEach
  void setUp() {
    amlService =
        new AmlService(
            amlCheckRepository, eventPublisher, checkService, analyticsThirdPillarRepository);

    MatchResponse mockResponse = mock(MatchResponse.class);
    ObjectMapper objectMapper = new ObjectMapper();

    ArrayNode emptyArrayNode = objectMapper.createArrayNode();
    when(mockResponse.results()).thenReturn(emptyArrayNode);

    ObjectNode emptyQueryNode = objectMapper.createObjectNode();
    when(mockResponse.query()).thenReturn(emptyQueryNode);

    when(checkService.match(any(), any())).thenReturn(mockResponse);
  }

  @Test
  @Transactional
  public void shouldAddDocumentCheckForUser() {
    User user = sampleUser().build();

    amlService.checkUserBeforeLogin(user, user, true);

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
  public void doesResidencyCheck() {
    User user = sampleUser().build();

    amlService.checkUserBeforeLogin(user, user, true);

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
  public void shouldAddPepAndSanctionChecks() {
    User user = sampleUser().build();
    Address address = Address.builder().countryCode("EE").build();

    List<AmlCheck> checks = amlService.addSanctionAndPepCheckIfMissing(user, address);

    assertThat(checks).hasSize(2);
    assertThat(checks).anyMatch(check -> check.getType() == POLITICALLY_EXPOSED_PERSON_AUTO);
    assertThat(checks).anyMatch(check -> check.getType() == SANCTION);
  }

  @Test
  @Transactional
  public void shouldVerifyAllChecksPassForThirdPillar() {
    User user = sampleUser().build();
    Address address = Address.builder().countryCode("EE").build();

    amlService.checkUserBeforeLogin(user, user, true);
    amlService.addSanctionAndPepCheckIfMissing(user, address);

    AmlCheck occupationCheck =
        AmlCheck.builder()
            .personalCode(user.getPersonalCode())
            .type(OCCUPATION)
            .success(true)
            .build();
    amlCheckRepository.save(occupationCheck);

    boolean allPassed = amlService.allChecksPassed(user, 3);
    assertThat(allPassed).isTrue();
  }

  @Test
  @Transactional
  public void shouldFailThirdPillarWhenMissingChecks() {
    User user = sampleUser().build();

    amlService.checkUserBeforeLogin(user, user, true);

    boolean allPassed = amlService.allChecksPassed(user, 3);
    assertThat(allPassed).isFalse();
  }

  @Test
  @Transactional
  public void shouldFailWhenSanctionMatchIsFound() {
    User user = sampleUser().build();
    Address address = Address.builder().countryCode("EE").build();

    ObjectMapper objectMapper = new ObjectMapper();
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

    List<AmlCheck> checks = amlService.addSanctionAndPepCheckIfMissing(user, address);

    assertThat(checks).anyMatch(check -> check.getType() == SANCTION && !check.isSuccess());
  }

  @Test
  @Transactional
  public void shouldNotAddResidencyCheckWhenNull() {
    User user = sampleUser().build();

    amlService.checkUserBeforeLogin(user, user, null);

    List<AmlCheck> checks =
        amlCheckRepository.findAllByPersonalCodeAndCreatedTimeAfter(
            user.getPersonalCode(), Instant.now().minusSeconds(3600));
    assertThat(checks).anyMatch(check -> check.getType() == DOCUMENT);
    assertThat(checks).anyMatch(check -> check.getType() == SK_NAME);
    assertThat(checks).noneMatch(check -> check.getType() == RESIDENCY_AUTO);
  }

  @Test
  @Transactional
  public void shouldAlwaysPassForSecondPillar() {
    User user = sampleUser().build();
    boolean allPassed = amlService.allChecksPassed(user, 2);
    assertThat(allPassed).isTrue();
  }

  @Test
  @Transactional
  public void shouldFailNameCheckWhenNamesDontMatch() {
    User user = sampleUser().firstName("John").lastName("Doe").build();
    User differingPerson = sampleUser().firstName("Jane").lastName("Dough").build();

    amlService.checkUserBeforeLogin(user, differingPerson, true);

    List<AmlCheck> checks =
        amlCheckRepository.findAllByPersonalCodeAndCreatedTimeAfter(
            user.getPersonalCode(), Instant.now().minusSeconds(3600));
    assertThat(checks).anyMatch(check -> check.getType() == SK_NAME && !check.isSuccess());
  }
}
