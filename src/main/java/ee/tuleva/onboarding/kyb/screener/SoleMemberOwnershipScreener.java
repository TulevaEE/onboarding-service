package ee.tuleva.onboarding.kyb.screener;

import static ee.tuleva.onboarding.kyb.KybCheckType.SOLE_MEMBER_OWNERSHIP;

import ee.tuleva.onboarding.kyb.KybCheck;
import ee.tuleva.onboarding.kyb.KybCompanyData;
import ee.tuleva.onboarding.kyb.KybRelatedPerson;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class SoleMemberOwnershipScreener implements KybScreener {

  @Override
  public List<KybCheck> screen(KybCompanyData companyData) {
    var persons = companyData.relatedPersons();
    if (persons.size() != 1) {
      return List.of();
    }

    var person = persons.getFirst();
    var totalOwnership = companyData.totalOwnership();
    boolean success =
        person.boardMember()
            && person.shareholder()
            && person.beneficialOwner()
            && totalOwnership.compareTo(BigDecimal.valueOf(100)) == 0;

    return List.of(
        new KybCheck(SOLE_MEMBER_OWNERSHIP, success, buildMetadata(person, totalOwnership)));
  }

  private Map<String, Object> buildMetadata(KybRelatedPerson person, BigDecimal totalOwnership) {
    var metadata = new HashMap<String, Object>();
    var personalCode = person.personalCode();
    if (personalCode != null) {
      metadata.put("personalCode", personalCode.toString());
    }
    metadata.put("ownershipPercent", person.ownershipPercent().toPlainString());
    metadata.put("totalOwnership", totalOwnership.toPlainString());
    return Map.copyOf(metadata);
  }
}
