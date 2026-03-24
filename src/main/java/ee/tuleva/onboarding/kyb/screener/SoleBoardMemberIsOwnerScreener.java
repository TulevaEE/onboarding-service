package ee.tuleva.onboarding.kyb.screener;

import static ee.tuleva.onboarding.kyb.KybCheckType.SOLE_BOARD_MEMBER_IS_OWNER;

import ee.tuleva.onboarding.kyb.KybCheck;
import ee.tuleva.onboarding.kyb.KybCompanyData;
import ee.tuleva.onboarding.kyb.KybRelatedPerson;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class SoleBoardMemberIsOwnerScreener implements KybScreener {

  @Override
  public List<KybCheck> screen(KybCompanyData companyData) {
    var persons = companyData.relatedPersons();
    if (persons.size() != 2) {
      return List.of();
    }

    var boardMembers = persons.stream().filter(KybRelatedPerson::boardMember).toList();
    if (boardMembers.size() != 1) {
      return List.of();
    }

    var boardMember = boardMembers.getFirst();
    boolean success = boardMember.shareholder() && boardMember.beneficialOwner();

    return List.of(
        new KybCheck(
            SOLE_BOARD_MEMBER_IS_OWNER,
            success,
            Map.of("boardMemberPersonalCode", boardMember.personalCode())));
  }
}
