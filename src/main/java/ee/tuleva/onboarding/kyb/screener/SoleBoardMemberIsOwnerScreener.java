package ee.tuleva.onboarding.kyb.screener;

import static ee.tuleva.onboarding.kyb.KybCheckType.SOLE_BOARD_MEMBER_IS_OWNER;

import ee.tuleva.onboarding.kyb.KybCheck;
import ee.tuleva.onboarding.kyb.KybCompanyData;
import ee.tuleva.onboarding.kyb.KybRelatedPerson;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class SoleBoardMemberIsOwnerScreener implements KybScreener {

  @Override
  public Optional<KybCheck> screen(KybCompanyData companyData) {
    var persons = companyData.relatedPersons();
    if (persons.size() != 2) {
      return Optional.empty();
    }

    var boardMembers = persons.stream().filter(KybRelatedPerson::boardMember).toList();
    if (boardMembers.size() != 1) {
      return Optional.empty();
    }

    var boardMember = boardMembers.getFirst();
    boolean success = boardMember.shareholder() && boardMember.beneficialOwner();

    return Optional.of(
        new KybCheck(
            SOLE_BOARD_MEMBER_IS_OWNER,
            success,
            Map.of("boardMemberPersonalCode", boardMember.personalCode())));
  }
}
