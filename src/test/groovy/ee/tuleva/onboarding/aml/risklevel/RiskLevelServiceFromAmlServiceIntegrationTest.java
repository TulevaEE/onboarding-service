package ee.tuleva.onboarding.aml.risklevel;

import static ee.tuleva.onboarding.aml.AmlCheckType.RISK_LEVEL;
import static ee.tuleva.onboarding.aml.AmlCheckType.TKF_RISK_LEVEL;
import static java.time.Duration.ofSeconds;
import static java.time.ZoneOffset.UTC;
import static java.time.temporal.ChronoUnit.DAYS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import ee.tuleva.onboarding.aml.AmlCheck;
import ee.tuleva.onboarding.aml.AmlCheckRepository;
import ee.tuleva.onboarding.aml.AmlCheckType;
import ee.tuleva.onboarding.time.ClockHolder;
import ee.tuleva.onboarding.time.TestClockHolder;
import java.io.UncheckedIOException;
import java.net.http.HttpTimeoutException;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@DataJpaTest
@Import({RiskLevelService.class, AmlServiceClient.class})
class RiskLevelServiceFromAmlServiceIntegrationTest {

  private static final String BASE_URL = "https://aml-service.test";
  private static final String API_TOKEN = "test-api-token-of-at-least-thirty-two-characters";
  private static final String TODAYS_RUN = "2020-01-01T02:15:00.123456Z";
  private static final String YESTERDAYS_RUN = "2019-12-31T02:15:00.123456Z";
  private static final String PERSONAL_CODE = "38001085718";

  private static final String THIRD_PILLAR_VIEW_METADATA =
      """
      {"version": "2.0", "level": 1, "value_risk_score": 2, "transaction_risk_score": 1,
       "pep_risk": 0, "sanction_risk": 0, "residency_risk": 1, "deposit_risk": 1,
       "too_many_withdrawals": 1, "historical_value_risk": 1, "historical_transaction_risk": 0,
       "young_shares_withdrawal": 0, "employer_correction": 0}
      """;

  private static final String TKF_VIEW_METADATA =
      """
      {"version": "1.6.0", "level": 1, "total_points": 105, "is_minor": false,
       "has_third_party_deposit": true, "flag_profile_data_missing": false,
       "rule_21_third_party": 100}
      """;

  @Autowired RiskLevelService riskLevelService;
  @MockitoBean AmlRiskReader amlRiskReader;
  @MockitoBean TkfRiskReader tkfRiskReader;
  @Autowired AmlCheckRepository amlCheckRepository;
  @Autowired TestEntityManager entityManager;
  @Autowired MockRestServiceServer server;
  @Autowired RestClient amlServiceRestClient;
  @Autowired RetryTemplate amlServiceRetryTemplate;
  @Autowired ApplicationEventPublisher eventPublisher;

  @BeforeEach
  void setUp() {
    ClockHolder.setClock(TestClockHolder.clock);
    server.reset();
  }

  @AfterEach
  void tearDown() {
    ClockHolder.setDefaultClock();
  }

  @Test
  void storesTheThirdPillarCheckTheMaterializedViewWouldHaveGivenForTheSameValues() {
    respond("third-pillar", TODAYS_RUN, "HIGH", THIRD_PILLAR_VIEW_METADATA);

    riskLevelService.writeThirdPillarRiskLevels(riskLevelService.fetchThirdPillarRiskLevels());
    entityManager.flush();
    entityManager.clear();

    assertThat(amlCheckRepository.findAll())
        .singleElement()
        .extracting(
            AmlCheck::getPersonalCode,
            AmlCheck::getType,
            AmlCheck::isSuccess,
            AmlCheck::getMetadata)
        .containsExactly(
            PERSONAL_CODE,
            RISK_LEVEL,
            false,
            metadataAsReadFromTheView(THIRD_PILLAR_VIEW_METADATA, 1));
    server.verify();
  }

  @Test
  void storesTheTkfCheckTheMaterializedViewWouldHaveGivenForTheSameValues() {
    respond("tkf", TODAYS_RUN, "HIGH", TKF_VIEW_METADATA);

    riskLevelService.writeTkfRiskLevels(riskLevelService.fetchTkfRiskLevels());
    entityManager.flush();
    entityManager.clear();

    assertThat(amlCheckRepository.findAll())
        .singleElement()
        .extracting(
            AmlCheck::getPersonalCode,
            AmlCheck::getType,
            AmlCheck::isSuccess,
            AmlCheck::getMetadata)
        .containsExactly(
            PERSONAL_CODE, TKF_RISK_LEVEL, false, metadataAsReadFromTheView(TKF_VIEW_METADATA, 1));
    verifyNoInteractions(amlRiskReader, tkfRiskReader);
    server.verify();
  }

  @Test
  void skipsAThirdPillarResultAlreadyWrittenFromTheMaterializedView() {
    persistFailedCheck(RISK_LEVEL, metadataAsReadFromTheView(THIRD_PILLAR_VIEW_METADATA, 1));
    respond("third-pillar", TODAYS_RUN, "HIGH", THIRD_PILLAR_VIEW_METADATA);

    riskLevelService.writeThirdPillarRiskLevels(riskLevelService.fetchThirdPillarRiskLevels());

    assertThat(amlCheckRepository.findAll()).hasSize(1);
    server.verify();
  }

  @Test
  void skipsATkfResultAlreadyWrittenFromTheMaterializedView() {
    persistFailedCheck(TKF_RISK_LEVEL, metadataAsReadFromTheView(TKF_VIEW_METADATA, 1));
    respond("tkf", TODAYS_RUN, "HIGH", TKF_VIEW_METADATA);

    riskLevelService.writeTkfRiskLevels(riskLevelService.fetchTkfRiskLevels());

    assertThat(amlCheckRepository.findAll()).hasSize(1);
    server.verify();
  }

  @Test
  void writesTheResultAgainOnceTheSameFailedCheckIsOlderThanSixMonths() {
    Clock now = ClockHolder.clock();
    ClockHolder.setClock(Clock.fixed(now.instant().minus(190, DAYS), UTC));
    persistFailedCheck(TKF_RISK_LEVEL, metadataAsReadFromTheView(TKF_VIEW_METADATA, 1));
    ClockHolder.setClock(now);
    respond("tkf", TODAYS_RUN, "HIGH", TKF_VIEW_METADATA);

    riskLevelService.writeTkfRiskLevels(riskLevelService.fetchTkfRiskLevels());

    assertThat(amlCheckRepository.findAll()).hasSize(2);
    server.verify();
  }

  @Test
  void writesTheResultAgainWhenTheSameCheckSucceeded() {
    entityManager.persistAndFlush(
        AmlCheck.builder()
            .personalCode(PERSONAL_CODE)
            .type(TKF_RISK_LEVEL)
            .success(true)
            .metadata(metadataAsReadFromTheView(TKF_VIEW_METADATA, 1))
            .build());
    entityManager.clear();
    respond("tkf", TODAYS_RUN, "HIGH", TKF_VIEW_METADATA);

    riskLevelService.writeTkfRiskLevels(riskLevelService.fetchTkfRiskLevels());

    assertThat(amlCheckRepository.findAll()).hasSize(2);
    server.verify();
  }

  @Test
  void writesNothingFromAnotherDaysRun() {
    respond("tkf", YESTERDAYS_RUN, "HIGH", TKF_VIEW_METADATA);

    assertThatThrownBy(riskLevelService::fetchTkfRiskLevels)
        .isInstanceOf(RiskLevelsRefusedException.class);

    assertThat(amlCheckRepository.findAll()).isEmpty();
    server.verify();
  }

  @Test
  void writesNothingBeforeTheFamilysFirstRun() {
    server.expect(once(), requestTo(url("tkf"))).andRespond(withResourceNotFound());

    assertThatThrownBy(riskLevelService::fetchTkfRiskLevels)
        .isInstanceOf(HttpClientErrorException.NotFound.class);

    assertThat(amlCheckRepository.findAll()).isEmpty();
    server.verify();
  }

  @Test
  void writesNothingWhenAmlServiceFails() {
    server.expect(times(2), requestTo(url("tkf"))).andRespond(withServerError());

    assertThatThrownBy(riskLevelService::fetchTkfRiskLevels)
        .isInstanceOf(HttpServerErrorException.class);

    assertThat(amlCheckRepository.findAll()).isEmpty();
    server.verify();
  }

  @Test
  void writesNothingWhenAmlServiceTimesOut() {
    server
        .expect(times(2), requestTo(url("tkf")))
        .andRespond(withException(new HttpTimeoutException("request timed out")));

    assertThatThrownBy(riskLevelService::fetchTkfRiskLevels)
        .isInstanceOf(ResourceAccessException.class);

    assertThat(amlCheckRepository.findAll()).isEmpty();
    server.verify();
  }

  @Test
  void asksNothingAndWritesNothingWithoutAnApiToken() {
    var withoutApiToken =
        new RiskLevelService(
            amlRiskReader,
            tkfRiskReader,
            new AmlServiceClient(
                amlServiceRestClient,
                amlServiceRetryTemplate,
                new AmlServiceProperties(true, BASE_URL, "", ofSeconds(5), ofSeconds(60))),
            amlCheckRepository,
            eventPublisher,
            TestClockHolder.clock);

    assertThatThrownBy(withoutApiToken::fetchTkfRiskLevels)
        .isInstanceOf(RiskLevelsRefusedException.class);

    assertThat(amlCheckRepository.findAll()).isEmpty();
    server.verify();
  }

  private void respond(String family, String assessedAt, String level, String metadata) {
    server
        .expect(once(), requestTo(url(family)))
        .andRespond(
            withSuccess(
                """
                {"family": "%s", "assessedAt": "%s", "results": [
                  {"personalCode": "%s", "level": "%s", "sampled": false, "metadata": %s}
                ]}
                """
                    .formatted(family, assessedAt, PERSONAL_CODE, level, metadata),
                APPLICATION_JSON));
  }

  private static String url(String family) {
    return BASE_URL + "/v1/risk-levels/" + family;
  }

  private void persistFailedCheck(AmlCheckType type, Map<String, Object> metadata) {
    entityManager.persistAndFlush(
        AmlCheck.builder()
            .personalCode(PERSONAL_CODE)
            .type(type)
            .success(false)
            .metadata(metadata)
            .build());
    entityManager.clear();
  }

  private static Map<String, Object> metadataAsReadFromTheView(String viewMetadata, int level) {
    try {
      Map<String, Object> metadata =
          new HashMap<>(
              new ObjectMapper()
                  .findAndRegisterModules()
                  .readValue(viewMetadata, new TypeReference<Map<String, Object>>() {}));
      metadata.put("level", level);
      return metadata;
    } catch (JsonProcessingException e) {
      throw new UncheckedIOException(e);
    }
  }

  @TestConfiguration
  static class AmlServiceTestConfiguration {

    @Bean
    RestClient.Builder amlServiceRestClientBuilder() {
      return RestClient.builder().baseUrl(BASE_URL);
    }

    @Bean
    MockRestServiceServer amlServiceServer(RestClient.Builder amlServiceRestClientBuilder) {
      return MockRestServiceServer.bindTo(amlServiceRestClientBuilder).build();
    }

    @Bean
    RestClient amlServiceRestClient(
        RestClient.Builder amlServiceRestClientBuilder, MockRestServiceServer amlServiceServer) {
      return amlServiceRestClientBuilder.build();
    }

    @Bean
    RetryTemplate amlServiceRetryTemplate() {
      return new AmlServiceConfiguration().amlServiceRetryTemplate();
    }

    @Bean
    AmlServiceProperties amlServiceProperties() {
      return new AmlServiceProperties(true, BASE_URL, API_TOKEN, ofSeconds(5), ofSeconds(60));
    }

    @Bean
    Clock clock() {
      return TestClockHolder.clock;
    }
  }
}
