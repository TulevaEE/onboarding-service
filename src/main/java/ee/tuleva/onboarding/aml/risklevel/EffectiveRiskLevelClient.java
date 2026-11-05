package ee.tuleva.onboarding.aml.risklevel;

import static ee.tuleva.onboarding.aml.risklevel.EffectiveRiskLevel.HIGH;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

@RequiredArgsConstructor
class EffectiveRiskLevelClient {

  private final RestClient effectiveRiskLevelRestClient;
  private final AmlServiceProperties properties;

  List<EffectiveRiskLevel> effectiveRiskLevels(String personalCode) {
    String apiToken =
        properties
            .configuredApiToken()
            .orElseThrow(
                () ->
                    new RiskLevelsRefusedException(
                        "aml-service API token is not configured, sending no request"));
    return toEffectiveRiskLevels(fetch(personalCode, apiToken));
  }

  private EffectiveRiskLevelsResponse fetch(String personalCode, String apiToken) {
    ResponseEntity<EffectiveRiskLevelsResponse> response =
        effectiveRiskLevelRestClient
            .get()
            .uri("/v1/risk-levels/effective/{personalCode}", personalCode)
            .accept(APPLICATION_JSON)
            .headers(headers -> headers.setBearerAuth(apiToken))
            .retrieve()
            .toEntity(EffectiveRiskLevelsResponse.class);
    EffectiveRiskLevelsResponse body = response.getBody();
    if (!response.getStatusCode().is2xxSuccessful() || body == null) {
      throw new RiskLevelsRefusedException(
          "aml-service answered without effective risk levels: status="
              + response.getStatusCode().value());
    }
    return body;
  }

  private static List<EffectiveRiskLevel> toEffectiveRiskLevels(
      EffectiveRiskLevelsResponse response) {
    List<@Nullable FamilyRiskLevel> families = required(response.families(), "families");
    List<EffectiveRiskLevel> recognised =
        families.stream()
            .map(EffectiveRiskLevelClient::recognisedLevel)
            .flatMap(Optional::stream)
            .toList();
    if (recognised.contains(HIGH)) {
      return recognised;
    }
    return IntStream.range(0, families.size())
        .mapToObj(index -> toEffectiveRiskLevel(index, families.get(index)))
        .toList();
  }

  private static Optional<EffectiveRiskLevel> recognisedLevel(@Nullable FamilyRiskLevel family) {
    return Optional.ofNullable(family)
        .map(FamilyRiskLevel::effective)
        .flatMap(
            effective ->
                Arrays.stream(EffectiveRiskLevel.values())
                    .filter(level -> level.name().equals(effective))
                    .findFirst());
  }

  private static EffectiveRiskLevel toEffectiveRiskLevel(
      int index, @Nullable FamilyRiskLevel family) {
    String field = "families[" + index + "]";
    String effective = required(required(family, field).effective(), field + ".effective");
    try {
      return EffectiveRiskLevel.valueOf(effective);
    } catch (IllegalArgumentException e) {
      throw new UnusableRiskLevelAnswerException(
          "aml-service answered an unknown risk level: field=" + field + ".effective");
    }
  }

  private static <T> T required(@Nullable T value, String field) {
    if (value == null) {
      throw new UnusableRiskLevelAnswerException(
          "aml-service answered without a required value: field=" + field);
    }
    return value;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  record EffectiveRiskLevelsResponse(@Nullable List<@Nullable FamilyRiskLevel> families) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record FamilyRiskLevel(@Nullable String effective) {}
}
