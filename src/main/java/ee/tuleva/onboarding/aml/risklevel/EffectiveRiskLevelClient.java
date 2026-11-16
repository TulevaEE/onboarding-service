package ee.tuleva.onboarding.aml.risklevel;

import static org.springframework.http.MediaType.APPLICATION_JSON;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
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

  List<EffectiveFamilyLevel> effectiveRiskLevels(String personalCode) {
    return effectiveRiskLevels("/v1/risk-levels/effective/{personalCode}", personalCode);
  }

  List<EffectiveFamilyLevel> effectiveCompanyRiskLevels(String registryCode) {
    List<EffectiveFamilyLevel> levels =
        effectiveRiskLevels("/v1/risk-levels/effective/companies/{registryCode}", registryCode);
    if (levels.isEmpty()) {
      throw new UnusableRiskLevelAnswerException(
          "aml-service answered no company risk level, so no nightly check has assessed it");
    }
    return levels;
  }

  private List<EffectiveFamilyLevel> effectiveRiskLevels(String uriTemplate, String code) {
    String apiToken =
        properties
            .configuredApiToken()
            .orElseThrow(
                () ->
                    new RiskLevelsRefusedException(
                        "aml-service API token is not configured, sending no request"));
    return toEffectiveRiskLevels(fetch(uriTemplate, code, apiToken));
  }

  private EffectiveRiskLevelsResponse fetch(String uriTemplate, String code, String apiToken) {
    ResponseEntity<EffectiveRiskLevelsResponse> response =
        effectiveRiskLevelRestClient
            .get()
            .uri(uriTemplate, code)
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

  private static List<EffectiveFamilyLevel> toEffectiveRiskLevels(
      EffectiveRiskLevelsResponse response) {
    List<@Nullable FamilyRiskLevel> families = required(response.families(), "families");
    List<EffectiveFamilyLevel> recognised =
        families.stream()
            .map(EffectiveRiskLevelClient::recognisedLevel)
            .flatMap(Optional::stream)
            .toList();
    if (recognised.stream().anyMatch(EffectiveFamilyLevel::isHigh)) {
      return recognised;
    }
    return IntStream.range(0, families.size())
        .mapToObj(index -> toEffectiveRiskLevel(index, families.get(index)))
        .toList();
  }

  private static Optional<EffectiveFamilyLevel> recognisedLevel(@Nullable FamilyRiskLevel family) {
    return Optional.ofNullable(family)
        .flatMap(
            answered ->
                Arrays.stream(EffectiveRiskLevel.values())
                    .filter(level -> level.name().equals(answered.effective()))
                    .findFirst()
                    .map(level -> answered.with(level)));
  }

  private static EffectiveFamilyLevel toEffectiveRiskLevel(
      int index, @Nullable FamilyRiskLevel family) {
    String field = "families[" + index + "]";
    FamilyRiskLevel answered = required(family, field);
    String effective = required(answered.effective(), field + ".effective");
    try {
      return answered.with(EffectiveRiskLevel.valueOf(effective));
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
  record FamilyRiskLevel(
      @Nullable String family,
      @Nullable String effective,
      @Nullable Instant decidedAt,
      @Nullable OverrideInForce override) {

    EffectiveFamilyLevel with(EffectiveRiskLevel level) {
      return new EffectiveFamilyLevel(family, level, assessedAt());
    }

    private @Nullable Instant assessedAt() {
      return override != null && override.setsTheLevel() ? override.createdAt() : decidedAt;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  record OverrideInForce(@Nullable String outcome, @Nullable Instant createdAt) {

    private static final String OVERRIDDEN = "OVERRIDDEN";

    boolean setsTheLevel() {
      return OVERRIDDEN.equals(outcome);
    }
  }
}
