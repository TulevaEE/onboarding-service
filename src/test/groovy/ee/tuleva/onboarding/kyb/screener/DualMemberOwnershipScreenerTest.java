package ee.tuleva.onboarding.kyb.screener;

import static ee.tuleva.onboarding.kyb.KybCheckType.DUAL_MEMBER_OWNERSHIP;
import static ee.tuleva.onboarding.kyb.KybTestFixtures.boardMemberOnly;
import static ee.tuleva.onboarding.kyb.KybTestFixtures.boardMemberOwner;
import static ee.tuleva.onboarding.kyb.KybTestFixtures.companyWith;
import static ee.tuleva.onboarding.kyb.KybTestFixtures.kybPerson;
import static ee.tuleva.onboarding.kyb.KybTestFixtures.shareholderOwner;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import ee.tuleva.onboarding.kyb.KybCheck;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DualMemberOwnershipScreenerTest {

  private final DualMemberOwnershipScreener screener = new DualMemberOwnershipScreener();

  @Test
  void twoBoardMembersBothShareholdersAndBeneficialOwnersWithFullOwnershipPasses() {
    var person1 = boardMemberOwner("38501010001", 50.0).build();
    var person2 = boardMemberOwner("38501010002", 50.0).build();
    var data = companyWith(person1, person2);

    var result = screener.screen(data);

    assertThat(result)
        .extracting(KybCheck::type, KybCheck::success)
        .containsExactly(tuple(DUAL_MEMBER_OWNERSHIP, true));
  }

  @Test
  void twoBoardMembersWithTotalOwnershipLessThan100Fails() {
    var person1 = boardMemberOwner("38501010001", 30.0).build();
    var person2 = boardMemberOwner("38501010002", 30.0).build();
    var data = companyWith(person1, person2);

    var result = screener.screen(data);

    assertThat(result)
        .extracting(KybCheck::type, KybCheck::success)
        .containsExactly(tuple(DUAL_MEMBER_OWNERSHIP, false));
  }

  @Test
  void twoBoardMembersWhereOneOwnsEverythingAndOtherIsBoardMemberOnlyPasses() {
    var person1 = boardMemberOwner("38501010001", 100.0).build();
    var person2 = boardMemberOnly("38501010002").build();
    var data = companyWith(person1, person2);

    var result = screener.screen(data);

    assertThat(result)
        .extracting(KybCheck::type, KybCheck::success)
        .containsExactly(tuple(DUAL_MEMBER_OWNERSHIP, true));
  }

  @Test
  void twoBoardMembersWhereMinorityShareholderIsNotABeneficialOwnerPasses() {
    var majorityOwner = boardMemberOwner("38501010001", 95.0).build();
    // A sub-25% shareholder is correctly not a beneficial owner under AML rules.
    var minorityShareholder =
        kybPerson("38501010002")
            .boardMember(true)
            .shareholder(true)
            .beneficialOwner(false)
            .ownershipPercent(BigDecimal.valueOf(5.0))
            .build();
    var data = companyWith(majorityOwner, minorityShareholder);

    var result = screener.screen(data);

    assertThat(result)
        .extracting(KybCheck::type, KybCheck::success)
        .containsExactly(tuple(DUAL_MEMBER_OWNERSHIP, true));
  }

  @Test
  void twoBoardMembersWhereNeitherIsABeneficialOwnerFails() {
    // The rule requires at least one person to be a shareholder, board member and beneficial owner.
    var person1 =
        kybPerson("38501010001")
            .boardMember(true)
            .shareholder(true)
            .beneficialOwner(false)
            .ownershipPercent(BigDecimal.valueOf(50.0))
            .build();
    var person2 =
        kybPerson("38501010002")
            .boardMember(true)
            .shareholder(true)
            .beneficialOwner(false)
            .ownershipPercent(BigDecimal.valueOf(50.0))
            .build();
    var data = companyWith(person1, person2);

    var result = screener.screen(data);

    assertThat(result)
        .extracting(KybCheck::type, KybCheck::success)
        .containsExactly(tuple(DUAL_MEMBER_OWNERSHIP, false));
  }

  @Test
  void twoBoardMembersWhereABeneficialOwnerIsNotAShareholderFails() {
    var person1 = boardMemberOwner("38501010001", 60.0).build();
    var person2 =
        kybPerson("38501010002")
            .boardMember(true)
            .shareholder(false)
            .beneficialOwner(true)
            .ownershipPercent(BigDecimal.valueOf(40.0))
            .build();
    var data = companyWith(person1, person2);

    var result = screener.screen(data);

    assertThat(result)
        .extracting(KybCheck::type, KybCheck::success)
        .containsExactly(tuple(DUAL_MEMBER_OWNERSHIP, false));
  }

  @Test
  void totalOwnershipIsStoredAsCanonicalStringForStableJsonRoundTrip() {
    // totalOwnership must be a String, not a BigDecimal. The AmlCheck.metadata jsonb column
    // deserializes JSON numbers as Double/Integer, so a BigDecimal value never equals its reloaded
    // form and KybDataChangeDetector raises a spurious DATA_CHANGED every screening (AML #78).
    var person1 = boardMemberOwner("38501010001", 50.0).build();
    var person2 = boardMemberOwner("38501010002", 50.0).build();
    var data = companyWith(person1, person2);

    var result = screener.screen(data);

    assertThat(result.getFirst().metadata()).isEqualTo(Map.of("totalOwnership", "100.0"));
  }

  @Test
  void doesNotApplyWhenOnlyOneBoardMemberOutOfTwoPersons() {
    var person1 = boardMemberOwner("38501010001", 50.0).build();
    var person2 = shareholderOwner("38501010002", 50.0).build();
    var data = companyWith(person1, person2);

    var result = screener.screen(data);

    assertThat(result).isEmpty();
  }

  @Test
  void doesNotApplyWhenOnlyOneRelatedPerson() {
    var person = boardMemberOwner("38501010001", 100.0).build();
    var data = companyWith(person);

    var result = screener.screen(data);

    assertThat(result).isEmpty();
  }
}
