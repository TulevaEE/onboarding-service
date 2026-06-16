package ee.tuleva.onboarding.kyb.screener;

import static ee.tuleva.onboarding.kyb.KybCheckType.SOLE_BOARD_MEMBER_IS_OWNER;
import static ee.tuleva.onboarding.kyb.KybTestFixtures.boardMemberOnly;
import static ee.tuleva.onboarding.kyb.KybTestFixtures.boardMemberOwner;
import static ee.tuleva.onboarding.kyb.KybTestFixtures.companyWith;
import static ee.tuleva.onboarding.kyb.KybTestFixtures.kybPerson;
import static ee.tuleva.onboarding.kyb.KybTestFixtures.shareholderOwner;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import ee.tuleva.onboarding.kyb.KybCheck;
import ee.tuleva.onboarding.kyb.PersonalCode;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class SoleBoardMemberIsOwnerScreenerTest {

  private final SoleBoardMemberIsOwnerScreener screener = new SoleBoardMemberIsOwnerScreener();

  @Test
  void soleBoardMemberWhoIsShareholderAndBeneficialOwnerPasses() {
    var boardMember = boardMemberOwner("38501010001", 50.0).build();
    var otherPerson = shareholderOwner("38501010002", 50.0).build();
    var data = companyWith(boardMember, otherPerson);

    var result = screener.screen(data);

    assertThat(result)
        .extracting(KybCheck::type, KybCheck::success)
        .containsExactly(tuple(SOLE_BOARD_MEMBER_IS_OWNER, true));
  }

  @Test
  void soleBoardMemberWhoIsNotShareholderFails() {
    var boardMember = boardMemberOnly("38501010001").build();
    var owner = shareholderOwner("38501010002", 100.0).build();
    var data = companyWith(boardMember, owner);

    var result = screener.screen(data);

    assertThat(result)
        .extracting(KybCheck::type, KybCheck::success)
        .containsExactly(tuple(SOLE_BOARD_MEMBER_IS_OWNER, false));
  }

  @Test
  void soleBoardMemberWhoIsShareholderButNotBeneficialOwnerFails() {
    var boardMember =
        kybPerson("38501010001")
            .boardMember(true)
            .shareholder(true)
            .ownershipPercent(BigDecimal.valueOf(50))
            .build();
    var otherPerson = shareholderOwner("38501010002", 50.0).build();
    var data = companyWith(boardMember, otherPerson);

    var result = screener.screen(data);

    assertThat(result)
        .extracting(KybCheck::type, KybCheck::success)
        .containsExactly(tuple(SOLE_BOARD_MEMBER_IS_OWNER, false));
  }

  @Test
  void handlesNullPersonalCodeForSoleBoardMember() {
    var boardMember = boardMemberOwner((PersonalCode) null, 50.0).build();
    var otherPerson = shareholderOwner("38501010002", 50.0).build();
    var data = companyWith(boardMember, otherPerson);

    var result = screener.screen(data);

    assertThat(result)
        .extracting(KybCheck::type, KybCheck::success)
        .containsExactly(tuple(SOLE_BOARD_MEMBER_IS_OWNER, true));
    assertThat(result.getFirst().metadata()).doesNotContainKey("boardMemberPersonalCode");
  }

  @Test
  void doesNotApplyWhenTwoBoardMembers() {
    var person1 = boardMemberOwner("38501010001", 50.0).build();
    var person2 = boardMemberOwner("38501010002", 50.0).build();
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
