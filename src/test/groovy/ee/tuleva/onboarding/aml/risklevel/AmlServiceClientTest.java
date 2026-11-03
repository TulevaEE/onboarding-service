package ee.tuleva.onboarding.aml.risklevel;

import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpHeaders.ACCEPT;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpStatus.FOUND;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.http.HttpTimeoutException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

class AmlServiceClientTest {

  private static final String BASE_URL = "https://aml-service.test";
  private static final String TKF_URL = BASE_URL + "/v1/risk-levels/tkf";
  private static final String API_TOKEN = "test-api-token-of-at-least-thirty-two-characters";

  private static final String TKF_RESPONSE =
      """
      {
        "family": "tkf",
        "assessedAt": "2026-10-28T02:15:00.123456Z",
        "results": [
          {
            "personalCode": "38001085718",
            "level": "HIGH",
            "sampled": false,
            "metadata": {
              "version": "1.6.0",
              "level": 1,
              "total_points": 105,
              "is_minor": false,
              "has_third_party_deposit": true,
              "flag_profile_data_missing": false,
              "rule_21_third_party": 100
            }
          },
          {
            "personalCode": "39901019992",
            "level": "MEDIUM",
            "sampled": true,
            "metadata": {
              "version": "1.6.0",
              "level": 2,
              "total_points": 20,
              "is_minor": true,
              "has_third_party_deposit": false,
              "flag_profile_data_missing": true,
              "rule_21_third_party": 0
            }
          }
        ]
      }
      """;

  private final RestClient.Builder restClientBuilder = RestClient.builder().baseUrl(BASE_URL);
  private final MockRestServiceServer server =
      MockRestServiceServer.bindTo(restClientBuilder).build();
  private final RestClient amlServiceRestClient = restClientBuilder.build();

  @Test
  void asksForTheFamilysRiskLevelsWithTheBearerToken() {
    server
        .expect(once(), requestTo(TKF_URL))
        .andExpect(method(GET))
        .andExpect(header(AUTHORIZATION, "Bearer " + API_TOKEN))
        .andExpect(header(ACCEPT, APPLICATION_JSON_VALUE))
        .andRespond(withSuccess(TKF_RESPONSE, APPLICATION_JSON));

    client(API_TOKEN).riskLevels("tkf");

    server.verify();
  }

  @Test
  void givesEachResultItsMaterializedViewLevelAndTheMetadataInItsJsonTypes() {
    server.expect(requestTo(TKF_URL)).andRespond(withSuccess(TKF_RESPONSE, APPLICATION_JSON));

    AssessedRiskLevels riskLevels = client(API_TOKEN).riskLevels("tkf");

    assertThat(riskLevels)
        .isEqualTo(
            new AssessedRiskLevels(
                Instant.parse("2026-10-28T02:15:00.123456Z"),
                List.of(
                    new RiskLevelResult(
                        "38001085718",
                        1,
                        Map.of(
                            "version",
                            "1.6.0",
                            "level",
                            1,
                            "total_points",
                            105,
                            "is_minor",
                            false,
                            "has_third_party_deposit",
                            true,
                            "flag_profile_data_missing",
                            false,
                            "rule_21_third_party",
                            100)),
                    new RiskLevelResult(
                        "39901019992",
                        2,
                        Map.of(
                            "version",
                            "1.6.0",
                            "level",
                            2,
                            "total_points",
                            20,
                            "is_minor",
                            true,
                            "has_third_party_deposit",
                            false,
                            "flag_profile_data_missing",
                            true,
                            "rule_21_third_party",
                            0)))));
  }

  @Test
  void refusesTheAnswerBeforeTheFamilysFirstRunWithoutAskingAgain() {
    server.expect(once(), requestTo(TKF_URL)).andRespond(withResourceNotFound());

    assertThatThrownBy(() -> client(API_TOKEN).riskLevels("tkf"))
        .isInstanceOf(HttpClientErrorException.NotFound.class);
    server.verify();
  }

  @Test
  void refusesARejectedTokenWithoutAskingAgain() {
    server.expect(once(), requestTo(TKF_URL)).andRespond(withStatus(UNAUTHORIZED));

    assertThatThrownBy(() -> client(API_TOKEN).riskLevels("tkf"))
        .isInstanceOf(HttpClientErrorException.Unauthorized.class);
    server.verify();
  }

  @Test
  void asksOnceMoreAfterAServerError() {
    server.expect(requestTo(TKF_URL)).andRespond(withServerError());
    server.expect(requestTo(TKF_URL)).andRespond(withSuccess(TKF_RESPONSE, APPLICATION_JSON));

    AssessedRiskLevels riskLevels = client(API_TOKEN).riskLevels("tkf");

    assertThat(riskLevels.results()).hasSize(2);
    server.verify();
  }

  @Test
  void refusesAServerErrorThatRepeats() {
    server.expect(times(2), requestTo(TKF_URL)).andRespond(withServerError());

    assertThatThrownBy(() -> client(API_TOKEN).riskLevels("tkf"))
        .isInstanceOf(HttpServerErrorException.class);
    server.verify();
  }

  @Test
  void refusesATimeoutThatRepeats() {
    server
        .expect(times(2), requestTo(TKF_URL))
        .andRespond(withException(new HttpTimeoutException("request timed out")));

    assertThatThrownBy(() -> client(API_TOKEN).riskLevels("tkf"))
        .isInstanceOf(ResourceAccessException.class);
    server.verify();
  }

  @Test
  void refusesAnAnswerThatIsNotASuccessEvenWithRiskLevelsInIt() {
    server
        .expect(once(), requestTo(TKF_URL))
        .andRespond(withStatus(FOUND).contentType(APPLICATION_JSON).body(TKF_RESPONSE));

    assertThatThrownBy(() -> client(API_TOKEN).riskLevels("tkf"))
        .isInstanceOf(RiskLevelsRefusedException.class);
    server.verify();
  }

  @Test
  void refusesASuccessWithoutRiskLevels() {
    server.expect(once(), requestTo(TKF_URL)).andRespond(withSuccess());

    assertThatThrownBy(() -> client(API_TOKEN).riskLevels("tkf"))
        .isInstanceOf(RiskLevelsRefusedException.class);
    server.verify();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"family\": \"tkf\", \"results\": []}",
        "{\"family\": \"tkf\", \"assessedAt\": null, \"results\": []}",
        "{\"family\": \"tkf\", \"assessedAt\": \"2026-10-28T02:15:00Z\"}",
        "{\"assessedAt\": \"2026-10-28T02:15:00Z\", \"results\": [null]}",
        "{\"assessedAt\": \"2026-10-28T02:15:00Z\", \"results\": [{\"level\": \"HIGH\","
            + " \"metadata\": {}}]}",
        "{\"assessedAt\": \"2026-10-28T02:15:00Z\", \"results\": [{\"personalCode\":"
            + " \"38888888888\", \"metadata\": {}}]}",
        "{\"assessedAt\": \"2026-10-28T02:15:00Z\", \"results\": [{\"personalCode\":"
            + " \"38888888888\", \"level\": \"HIGH\"}]}",
        "{\"assessedAt\": \"2026-10-28T02:15:00Z\", \"results\": [{\"personalCode\":"
            + " \"38888888888\", \"level\": \"HIGH\", \"metadata\": null}]}"
      })
  void refusesAnAnswerMissingARequiredValue(String body) {
    server.expect(once(), requestTo(TKF_URL)).andRespond(withSuccess(body, APPLICATION_JSON));

    assertThatThrownBy(() -> client(API_TOKEN).riskLevels("tkf"))
        .isInstanceOf(RiskLevelsRefusedException.class);
    server.verify();
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "   "})
  void sendsNoRequestWithoutAnApiToken(@Nullable String apiToken) {
    assertThatThrownBy(() -> client(apiToken).riskLevels("tkf"))
        .isInstanceOf(RiskLevelsRefusedException.class);
    server.verify();
  }

  private AmlServiceClient client(@Nullable String apiToken) {
    return new AmlServiceClient(
        amlServiceRestClient,
        AmlServiceConfiguration.amlServiceRetryTemplate(),
        new AmlServiceProperties(true, BASE_URL, apiToken, ofSeconds(5), ofSeconds(60)));
  }
}
