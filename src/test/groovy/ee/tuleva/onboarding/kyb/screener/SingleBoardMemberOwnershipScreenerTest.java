package ee.tuleva.onboarding.kyb.screener;

import static ee.tuleva.onboarding.kyb.KybCheckType.SINGLE_BOARD_MEMBER_OWNERSHIP;
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

class SingleBoardMemberOwnershipScreenerTest {

  private final SingleBoardMemberOwnershipScreener screener =
      new SingleBoardMemberOwnershipScreener();

  @Test
  void soleBoardMemberWhoIsShareholderAndBeneficialOwnerPasses() {
    var boardMember = boardMemberOwner("38501010001", 50.0).build();
    var otherPerson = shareholderOwner("38501010002", 50.0).build();
    var data = companyWith(boardMember, otherPerson);

    var result = screener.screen(data);

    assertThat(result)
        .extracting(KybCheck::type, KybCheck::success)
        .containsExactly(tuple(SINGLE_BOARD_MEMBER_OWNERSHIP, true));
  }

  @Test
  void failsWhenTheSoleBoardMemberOwnsNoShares() {
    // The board member must also be a shareholder. A pure director with no stake fails,
    // even though the other person is the beneficial owner.
    var boardMember = boardMemberOnly("38501010001").build();
    var owner = shareholderOwner("38501010002", 100.0).build();
    var data = companyWith(boardMember, owner);

    var result = screener.screen(data);

    assertThat(result)
        .extracting(KybCheck::type, KybCheck::success)
        .containsExactly(tuple(SINGLE_BOARD_MEMBER_OWNERSHIP, false));
  }

  @Test
  void boardMemberIsAShareholderButNotTheBeneficialOwnerPasses() {
    // The board member need not be the beneficial owner, only a shareholder.
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
        .containsExactly(tuple(SINGLE_BOARD_MEMBER_OWNERSHIP, true));
  }

  @Test
  void failsWhenNeitherPersonIsABeneficialOwner() {
    var boardMember =
        kybPerson("38501010001")
            .boardMember(true)
            .shareholder(true)
            .ownershipPercent(BigDecimal.valueOf(50))
            .build();
    var otherPerson =
        kybPerson("38501010002").shareholder(true).ownershipPercent(BigDecimal.valueOf(50)).build();
    var data = companyWith(boardMember, otherPerson);

    var result = screener.screen(data);

    assertThat(result)
        .extracting(KybCheck::type, KybCheck::success)
        .containsExactly(tuple(SINGLE_BOARD_MEMBER_OWNERSHIP, false));
  }

  @Test
  void failsWhenABeneficialOwnerIsNotAShareholder() {
    var boardMember = boardMemberOwner("38501010001", 100.0).build();
    var otherPerson =
        kybPerson("38501010002")
            .beneficialOwner(true)
            .ownershipPercent(BigDecimal.valueOf(0))
            .build();
    var data = companyWith(boardMember, otherPerson);

    var result = screener.screen(data);

    assertThat(result)
        .extracting(KybCheck::type, KybCheck::success)
        .containsExactly(tuple(SINGLE_BOARD_MEMBER_OWNERSHIP, false));
  }

  @Test
  void failsWhenOwnershipDoesNotTotal100() {
    var boardMember = boardMemberOwner("38501010001", 50.0).build();
    var otherPerson = shareholderOwner("38501010002", 30.0).build();
    var data = companyWith(boardMember, otherPerson);

    var result = screener.screen(data);

    assertThat(result)
        .extracting(KybCheck::type, KybCheck::success)
        .containsExactly(tuple(SINGLE_BOARD_MEMBER_OWNERSHIP, false));
  }

  @Test
  void handlesNullPersonalCodeForSoleBoardMember() {
    var boardMember = boardMemberOwner((PersonalCode) null, 50.0).build();
    var otherPerson = shareholderOwner("38501010002", 50.0).build();
    var data = companyWith(boardMember, otherPerson);

    var result = screener.screen(data);

    assertThat(result)
        .extracting(KybCheck::type, KybCheck::success)
        .containsExactly(tuple(SINGLE_BOARD_MEMBER_OWNERSHIP, true));
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
