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
    boolean hasNonNaturalPerson = persons.stream().anyMatch(p -> !p.naturalPerson());
    boolean success =
        !tooManyPersons
            && !hasUnidentifiedPerson
            && !hasNonNaturalPerson
            && ownershipStructureSupported(persons);

    return List.of(new KybCheck(COMPANY_STRUCTURE, success, buildMetadata(persons)));
  }

  // Fail closed: only structures an ownership rule covers are supported — a sole member, or two
  // members where at least one is a board member. Anything else (no related persons, or two
  // non-board owners) would otherwise pass with no ownership rule ever firing.
  private boolean ownershipStructureSupported(List<KybRelatedPerson> persons) {
    long boardMembers = persons.stream().filter(KybRelatedPerson::boardMember).count();
    return switch (persons.size()) {
      case 1 -> true;
      case 2 -> boardMembers >= 1;
      default -> false;
    };
  }

  private Map<String, Object> buildMetadata(List<KybRelatedPerson> persons) {
    return Map.of(
        "relatedPersonCount", persons.size(),
        "allIdentified", persons.stream().noneMatch(p -> p.personalCode() == null),
        "allNatural", persons.stream().allMatch(KybRelatedPerson::naturalPerson));
  }
}
