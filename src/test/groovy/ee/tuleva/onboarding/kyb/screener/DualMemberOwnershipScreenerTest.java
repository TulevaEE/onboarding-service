package ee.tuleva.onboarding.kyb.screener;

import static ee.tuleva.onboarding.kyb.KybCheckType.DUAL_MEMBER_OWNERSHIP;
import static ee.tuleva.onboarding.kyb.KybTestFixtures.boardMemberOwner;
import static ee.tuleva.onboarding.kyb.KybTestFixtures.companyWith;
import static ee.tuleva.onboarding.kyb.KybTestFixtures.kybPerson;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class DualMemberOwnershipScreenerTest {

  private final DualMemberOwnershipScreener screener = new DualMemberOwnershipScreener();

  @Test
  void twoBoardMembersBothShareholdersAndBeneficialOwnersWithFullOwnershipPasses() {
    var person1 = boardMemberOwner("38501010001", 50.0).build();
    var person2 = boardMemberOwner("38501010002", 50.0).build();
    var data = companyWith(person1, person2);

    var result = screener.screen(data);

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().type()).isEqualTo(DUAL_MEMBER_OWNERSHIP);
    assertThat(result.getFirst().success()).isTrue();
  }

  @Test
  void twoBoardMembersWithTotalOwnershipLessThan100Fails() {
    var person1 = boardMemberOwner("38501010001", 30.0).build();
    var person2 = boardMemberOwner("38501010002", 30.0).build();
    var data = companyWith(person1, person2);

    var result = screener.screen(data);

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().type()).isEqualTo(DUAL_MEMBER_OWNERSHIP);
    assertThat(result.getFirst().success()).isFalse();
  }

  @Test
  void twoBoardMembersWhereOneIsNotShareholderFails() {
    var person1 = boardMemberOwner("38501010001", 100.0).build();
    var person2 = kybPerson("38501010002").boardMember(true).build();
    var data = companyWith(person1, person2);

    var result = screener.screen(data);

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().type()).isEqualTo(DUAL_MEMBER_OWNERSHIP);
    assertThat(result.getFirst().success()).isFalse();
  }

  @Test
  void doesNotApplyWhenOnlyOneBoardMemberOutOfTwoPersons() {
    var person1 = boardMemberOwner("38501010001", 50.0).build();
    var person2 =
        kybPerson("38501010002")
            .shareholder(true)
            .beneficialOwner(true)
            .ownershipPercent(BigDecimal.valueOf(50))
            .build();
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
