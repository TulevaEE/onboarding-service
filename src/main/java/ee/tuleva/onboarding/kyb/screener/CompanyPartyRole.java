package ee.tuleva.onboarding.kyb.screener;

import ee.tuleva.onboarding.kyb.KybRelatedPerson;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

enum CompanyPartyRole {
  BOARD_MEMBER(KybRelatedPerson::boardMember),
  SHAREHOLDER(KybRelatedPerson::shareholder),
  BENEFICIAL_OWNER(KybRelatedPerson::beneficialOwner);

  private final Predicate<KybRelatedPerson> heldBy;

  CompanyPartyRole(final Predicate<KybRelatedPerson> heldBy) {
    this.heldBy = heldBy;
  }

  static List<CompanyPartyRole> heldBy(final KybRelatedPerson person) {
    return Arrays.stream(values()).filter(role -> role.heldBy.test(person)).toList();
  }
}
