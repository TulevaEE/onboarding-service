package ee.tuleva.onboarding.kyb.screener;

import static ee.tuleva.onboarding.kyb.KybCheckType.SINGLE_BOARD_MEMBER_OWNERSHIP;

import ee.tuleva.onboarding.kyb.KybCheck;
import ee.tuleva.onboarding.kyb.KybCompanyData;
import ee.tuleva.onboarding.kyb.KybRelatedPerson;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class SingleBoardMemberOwnershipScreener implements KybScreener {

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

    boolean hasBeneficialOwner = persons.stream().anyMatch(KybRelatedPerson::beneficialOwner);

    boolean everyBeneficialOwnerIsAShareholder =
        persons.stream().allMatch(p -> !p.beneficialOwner() || p.shareholder());

    BigDecimal totalOwnership =
        persons.stream()
            .map(KybRelatedPerson::ownershipPercent)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    boolean success =
        hasBeneficialOwner
            && everyBeneficialOwnerIsAShareholder
            && totalOwnership.compareTo(BigDecimal.valueOf(100)) == 0;

    return List.of(
        new KybCheck(SINGLE_BOARD_MEMBER_OWNERSHIP, success, buildMetadata(boardMember)));
  }

  private Map<String, Object> buildMetadata(KybRelatedPerson boardMember) {
    var personalCode = boardMember.personalCode();
    return personalCode == null
        ? Map.of()
        : Map.of("boardMemberPersonalCode", personalCode.toString());
  }
}
