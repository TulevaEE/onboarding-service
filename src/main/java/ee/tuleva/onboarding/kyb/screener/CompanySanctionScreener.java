package ee.tuleva.onboarding.kyb.screener;

import static ee.tuleva.onboarding.kyb.KybCheckType.COMPANY_PEP;
import static ee.tuleva.onboarding.kyb.KybCheckType.COMPANY_SANCTION;

import ee.tuleva.onboarding.aml.sanctions.MatchResponse;
import ee.tuleva.onboarding.aml.sanctions.PepAndSanctionCheckService;
import ee.tuleva.onboarding.kyb.KybCheck;
import ee.tuleva.onboarding.kyb.KybCompanyData;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;

@Component
@RequiredArgsConstructor
public class CompanySanctionScreener implements KybScreener {

  static final Set<String> EU_COUNTRY_CODES =
      Set.of(
          "at", "be", "bg", "hr", "cy", "cz", "dk", "ee", "fi", "fr", "de", "gr", "hu", "ie", "it",
          "lv", "lt", "lu", "mt", "nl", "pl", "pt", "ro", "sk", "si", "es", "se");

  private final PepAndSanctionCheckService sanctionCheckService;

  @Override
  public List<KybCheck> screen(KybCompanyData companyData) {
    var response = sanctionCheckService.matchCompany(companyData.company());

    return List.of(sanctionCheck(response), pepCheck(response));
  }

  private KybCheck sanctionCheck(MatchResponse response) {
    boolean hasSanctionMatch = hasMatchWithTopic(response.results(), "sanction");
    return new KybCheck(COMPANY_SANCTION, !hasSanctionMatch, buildMetadata(response));
  }

  private KybCheck pepCheck(MatchResponse response) {
    boolean hasNonEuPep = hasNonEuPepMatch(response.results());
    return new KybCheck(COMPANY_PEP, !hasNonEuPep, buildMetadata(response));
  }

  private boolean hasMatchWithTopic(ArrayNode results, String topicPrefix) {
    for (JsonNode result : results) {
      if (!result.path("match").asBoolean(false)) {
        continue;
      }
      for (JsonNode topic : result.path("properties").path("topics")) {
        if (topic.asText().startsWith(topicPrefix)) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean hasNonEuPepMatch(ArrayNode results) {
    for (JsonNode result : results) {
      if (!result.path("match").asBoolean(false)) {
        continue;
      }
      boolean isPep = false;
      for (JsonNode topic : result.path("properties").path("topics")) {
        if (topic.asText().startsWith("role")) {
          isPep = true;
          break;
        }
      }
      if (isPep && hasNonEuCountry(result)) {
        return true;
      }
    }
    return false;
  }

  private boolean hasNonEuCountry(JsonNode result) {
    var countries = result.path("properties").path("country");
    if (countries.isEmpty()) {
      return true;
    }
    for (JsonNode country : countries) {
      if (!EU_COUNTRY_CODES.contains(country.asText().toLowerCase())) {
        return true;
      }
    }
    return false;
  }

  private Map<String, Object> buildMetadata(MatchResponse response) {
    return Map.of("results", response.results().toString(), "query", response.query().toString());
  }
}
