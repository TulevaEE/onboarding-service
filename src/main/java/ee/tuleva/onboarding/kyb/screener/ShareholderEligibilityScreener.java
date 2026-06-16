package ee.tuleva.onboarding.kyb.screener;

import static ee.tuleva.onboarding.kyb.KybCheckType.SHAREHOLDER_ELIGIBILITY;

import ee.tuleva.onboarding.kyb.KybCheck;
import ee.tuleva.onboarding.kyb.KybCompanyData;
import ee.tuleva.onboarding.kyb.KybRelatedPerson;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ShareholderEligibilityScreener implements KybScreener {

  private static final int MAX_SHAREHOLDERS = 2;
  private static final BigDecimal FULL_OWNERSHIP = BigDecimal.valueOf(100);

  @Override
  public List<KybCheck> screen(KybCompanyData companyData) {
    var shareholders =
        companyData.relatedPersons().stream().filter(KybRelatedPerson::shareholder).toList();

    boolean validCount = !shareholders.isEmpty() && shareholders.size() <= MAX_SHAREHOLDERS;
    boolean allIdentified = shareholders.stream().allMatch(p -> p.personalCode() != null);
    boolean hasOwnerDirector =
        shareholders.stream().anyMatch(p -> p.boardMember() && p.beneficialOwner());
    BigDecimal totalOwnership =
        shareholders.stream()
            .map(KybRelatedPerson::ownershipPercent)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    boolean fullOwnership = totalOwnership.compareTo(FULL_OWNERSHIP) == 0;

    boolean success = validCount && allIdentified && hasOwnerDirector && fullOwnership;

    return List.of(
        new KybCheck(
            SHAREHOLDER_ELIGIBILITY,
            success,
            Map.of(
                "shareholderCount", shareholders.size(),
                "totalOwnership", totalOwnership,
                "allIdentified", allIdentified,
                "hasOwnerDirector", hasOwnerDirector)));
  }
}
