package ee.tuleva.onboarding.kyb.screener;

import static ee.tuleva.onboarding.kyb.KybCheckType.DUAL_MEMBER_OWNERSHIP;

import ee.tuleva.onboarding.kyb.KybCheck;
import ee.tuleva.onboarding.kyb.KybCompanyData;
import ee.tuleva.onboarding.kyb.KybRelatedPerson;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class DualMemberOwnershipScreener implements KybScreener {

  @Override
  public Optional<KybCheck> screen(KybCompanyData companyData) {
    var persons = companyData.relatedPersons();
    if (persons.size() != 2) {
      return Optional.empty();
    }

    var boardMembers = persons.stream().filter(KybRelatedPerson::boardMember).toList();
    if (boardMembers.size() != 2) {
      return Optional.empty();
    }

    boolean allShareholdersAndBeneficialOwners =
        persons.stream().allMatch(p -> p.shareholder() && p.beneficialOwner());

    BigDecimal totalOwnership =
        persons.stream()
            .map(KybRelatedPerson::ownershipPercent)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    boolean success =
        allShareholdersAndBeneficialOwners
            && totalOwnership.compareTo(BigDecimal.valueOf(100)) == 0;

    return Optional.of(
        new KybCheck(DUAL_MEMBER_OWNERSHIP, success, Map.of("totalOwnership", totalOwnership)));
  }
}
