package ee.tuleva.onboarding.kyb.screener;

import static ee.tuleva.onboarding.kyb.KybCheckType.COMPANY_STRUCTURE;

import ee.tuleva.onboarding.kyb.KybCheck;
import ee.tuleva.onboarding.kyb.KybCompanyData;
import ee.tuleva.onboarding.kyb.KybRelatedPerson;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class CompanyStructureScreener implements KybScreener {

  private static final int MAX_RELATED_PERSONS = 2;

  @Override
  public List<KybCheck> screen(KybCompanyData companyData) {
    var persons = companyData.relatedPersons();
    boolean tooManyPersons = persons.size() > MAX_RELATED_PERSONS;
    boolean hasUnidentifiedPerson = persons.stream().anyMatch(p -> p.personalCode() == null);
    boolean success = !tooManyPersons && !hasUnidentifiedPerson;

    return List.of(new KybCheck(COMPANY_STRUCTURE, success, buildMetadata(persons)));
  }

  private Map<String, Object> buildMetadata(List<KybRelatedPerson> persons) {
    return Map.of(
        "relatedPersonCount", persons.size(),
        "allIdentified", persons.stream().noneMatch(p -> p.personalCode() == null));
  }
}
