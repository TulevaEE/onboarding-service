package ee.tuleva.onboarding.kyb.screener;

import static ee.tuleva.onboarding.kyb.KybCheckType.SOLE_MEMBER_OWNERSHIP;

import ee.tuleva.onboarding.kyb.KybCheck;
import ee.tuleva.onboarding.kyb.KybCompanyData;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class SoleMemberOwnershipScreener implements KybScreener {

  @Override
  public Optional<KybCheck> screen(KybCompanyData companyData) {
    var persons = companyData.relatedPersons();
    if (persons.size() != 1) {
      return Optional.empty();
    }

    var person = persons.getFirst();
    boolean success =
        person.boardMember()
            && person.shareholder()
            && person.beneficialOwner()
            && person.ownershipPercent().compareTo(BigDecimal.valueOf(100)) == 0;

    return Optional.of(
        new KybCheck(
            SOLE_MEMBER_OWNERSHIP,
            success,
            Map.of(
                "personalCode",
                person.personalCode(),
                "ownershipPercent",
                person.ownershipPercent())));
  }
}
