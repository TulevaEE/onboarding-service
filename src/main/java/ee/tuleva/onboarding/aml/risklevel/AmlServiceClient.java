package ee.tuleva.onboarding.aml.risklevel;

import static org.springframework.http.MediaType.APPLICATION_JSON;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

@RequiredArgsConstructor
class AmlServiceClient {

  private final RestClient amlServiceRestClient;
  private final RetryTemplate amlServiceRetryTemplate;
  private final AmlServiceProperties properties;

  AssessedRiskLevels riskLevels(String family) {
    String apiToken =
        properties
            .configuredApiToken()
            .orElseThrow(
                () ->
                    new RiskLevelsRefusedException(
                        "aml-service API token is not configured, sending no request: family="
                            + family));
    return toAssessedRiskLevels(
        family, amlServiceRetryTemplate.invoke(() -> fetch(family, apiToken)));
  }

  private RiskLevelsResponse fetch(String family, String apiToken) {
    ResponseEntity<RiskLevelsResponse> response =
        amlServiceRestClient
            .get()
            .uri("/v1/risk-levels/{family}", family)
            .accept(APPLICATION_JSON)
            .headers(headers -> headers.setBearerAuth(apiToken))
            .retrieve()
            .toEntity(RiskLevelsResponse.class);
    RiskLevelsResponse body = response.getBody();
    if (!response.getStatusCode().is2xxSuccessful() || body == null) {
      throw new RiskLevelsRefusedException(
          "aml-service answered without risk levels: family="
              + family
              + ", status="
              + response.getStatusCode().value());
    }
    return body;
  }

  private static AssessedRiskLevels toAssessedRiskLevels(
      String family, RiskLevelsResponse response) {
    Instant assessedAt = required(response.assessedAt(), family, "assessedAt");
    List<@Nullable DeliveredRiskLevel> results = required(response.results(), family, "results");
    return new AssessedRiskLevels(
        assessedAt,
        IntStream.range(0, results.size())
            .mapToObj(index -> toRiskLevelResult(family, index, results.get(index)))
            .toList());
  }

  private static RiskLevelResult toRiskLevelResult(
      String family, int index, @Nullable DeliveredRiskLevel result) {
    String field = "results[" + index + "]";
    DeliveredRiskLevel present = required(result, family, field);
    return new RiskLevelResult(
        required(present.personalCode(), family, field + ".personalCode"),
        required(present.level(), family, field + ".level").riskLevel,
        required(present.metadata(), family, field + ".metadata"));
  }

  private static <T> T required(@Nullable T value, String family, String field) {
    if (value == null) {
      throw new RiskLevelsRefusedException(
          "aml-service answered without a required value: family=" + family + ", field=" + field);
    }
    return value;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  record RiskLevelsResponse(
      @Nullable Instant assessedAt, @Nullable List<@Nullable DeliveredRiskLevel> results) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record DeliveredRiskLevel(
      @Nullable String personalCode,
      @Nullable DeliveredLevel level,
      @Nullable Map<String, Object> metadata) {}

  @RequiredArgsConstructor
  enum DeliveredLevel {
    HIGH(1),
    MEDIUM(2);

    private final int riskLevel;
  }
}
