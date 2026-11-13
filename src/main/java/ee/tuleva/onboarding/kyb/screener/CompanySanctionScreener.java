package ee.tuleva.onboarding.kyb.screener;

import static ee.tuleva.onboarding.kyb.KybCheckType.COMPANY_PEP;
import static ee.tuleva.onboarding.kyb.KybCheckType.COMPANY_SANCTION;
import static java.util.function.Predicate.not;

import ee.tuleva.onboarding.aml.sanctions.MatchResponse;
import ee.tuleva.onboarding.aml.sanctions.PepAndSanctionCheckService;
import ee.tuleva.onboarding.aml.sanctions.ScreenedCompany;
import ee.tuleva.onboarding.country.EuropeanEconomicArea;
import ee.tuleva.onboarding.kyb.KybCheck;
import ee.tuleva.onboarding.kyb.KybCompanyData;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;

@Component
@RequiredArgsConstructor
public class CompanySanctionScreener implements KybScreener {

  private final PepAndSanctionCheckService sanctionCheckService;

  @Override
  public List<KybCheck> screen(KybCompanyData companyData) {
    var company = companyData.company();
    var response =
        sanctionCheckService.matchCompany(
            new ScreenedCompany(company.name(), company.registryCode().value()));

    return List.of(sanctionCheck(response), pepCheck(response));
  }

  private KybCheck sanctionCheck(MatchResponse response) {
    boolean hasSanctionMatch = hasMatchWithTopic(response.results(), "sanction");
    return new KybCheck(COMPANY_SANCTION, !hasSanctionMatch, buildMetadata(response));
  }

  private KybCheck pepCheck(MatchResponse response) {
    boolean hasPepOutsideTheEea = hasPepMatchOutsideTheEea(response.results());
    return new KybCheck(COMPANY_PEP, !hasPepOutsideTheEea, buildMetadata(response));
  }

  private boolean hasMatchWithTopic(ArrayNode results, String topicPrefix) {
    for (JsonNode result : results) {
      if (!result.path("match").asBoolean(false)) {
        continue;
      }
      for (JsonNode topic : result.path("properties").path("topics")) {
        if (topic.asString().startsWith(topicPrefix)) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean hasPepMatchOutsideTheEea(ArrayNode results) {
    for (JsonNode result : results) {
      if (!result.path("match").asBoolean(false)) {
        continue;
      }
      boolean isPep = false;
      for (JsonNode topic : result.path("properties").path("topics")) {
        if (topic.asString().startsWith("role")) {
          isPep = true;
          break;
        }
      }
      if (isPep && hasCountryOutsideTheEea(result)) {
        return true;
      }
    }
    return false;
  }

  private boolean hasCountryOutsideTheEea(JsonNode result) {
    var countries = result.path("properties").path("country");
    return countries.isEmpty()
        || countries
            .valueStream()
            .map(JsonNode::asString)
            .anyMatch(not(EuropeanEconomicArea::includes));
  }

  private Map<String, Object> buildMetadata(MatchResponse response) {
    return Map.of("results", response.results().toString(), "query", response.query().toString());
  }
}
