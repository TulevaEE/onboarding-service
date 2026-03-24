package ee.tuleva.onboarding.kyb.screener;

import static ee.tuleva.onboarding.kyb.CompanyStatus.R;
import static ee.tuleva.onboarding.kyb.KybCheckType.SOLE_BOARD_MEMBER_IS_OWNER;
import static ee.tuleva.onboarding.kyb.KybKycStatus.UNKNOWN;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.kyb.KybCompanyData;
import ee.tuleva.onboarding.kyb.KybRelatedPerson;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class SoleBoardMemberIsOwnerScreenerTest {

  private final SoleBoardMemberIsOwnerScreener screener = new SoleBoardMemberIsOwnerScreener();

  @Test
  void soleBoardMemberWhoIsShareholderAndBeneficialOwnerPasses() {
    var boardMember =
        new KybRelatedPerson("38501010001", true, true, true, BigDecimal.valueOf(50), UNKNOWN);
    var otherPerson =
        new KybRelatedPerson("38501010002", false, true, true, BigDecimal.valueOf(50), UNKNOWN);
    var data = new KybCompanyData("12345678", "38501010001", R, List.of(boardMember, otherPerson));

    var result = screener.screen(data);

    assertThat(result).isPresent();
    assertThat(result.get().type()).isEqualTo(SOLE_BOARD_MEMBER_IS_OWNER);
    assertThat(result.get().success()).isTrue();
  }

  @Test
  void soleBoardMemberWhoIsNotShareholderFails() {
    var boardMember =
        new KybRelatedPerson("38501010001", true, false, false, BigDecimal.ZERO, UNKNOWN);
    var owner =
        new KybRelatedPerson("38501010002", false, true, true, BigDecimal.valueOf(100), UNKNOWN);
    var data = new KybCompanyData("12345678", "38501010001", R, List.of(boardMember, owner));

    var result = screener.screen(data);

    assertThat(result).isPresent();
    assertThat(result.get().type()).isEqualTo(SOLE_BOARD_MEMBER_IS_OWNER);
    assertThat(result.get().success()).isFalse();
  }

  @Test
  void soleBoardMemberWhoIsShareholderButNotBeneficialOwnerFails() {
    var boardMember =
        new KybRelatedPerson("38501010001", true, true, false, BigDecimal.valueOf(50), UNKNOWN);
    var otherPerson =
        new KybRelatedPerson("38501010002", false, true, true, BigDecimal.valueOf(50), UNKNOWN);
    var data = new KybCompanyData("12345678", "38501010001", R, List.of(boardMember, otherPerson));

    var result = screener.screen(data);

    assertThat(result).isPresent();
    assertThat(result.get().type()).isEqualTo(SOLE_BOARD_MEMBER_IS_OWNER);
    assertThat(result.get().success()).isFalse();
  }

  @Test
  void doesNotApplyWhenTwoBoardMembers() {
    var person1 =
        new KybRelatedPerson("38501010001", true, true, true, BigDecimal.valueOf(50), UNKNOWN);
    var person2 =
        new KybRelatedPerson("38501010002", true, true, true, BigDecimal.valueOf(50), UNKNOWN);
    var data = new KybCompanyData("12345678", "38501010001", R, List.of(person1, person2));

    var result = screener.screen(data);

    assertThat(result).isEmpty();
  }

  @Test
  void doesNotApplyWhenOnlyOneRelatedPerson() {
    var person =
        new KybRelatedPerson("38501010001", true, true, true, BigDecimal.valueOf(100), UNKNOWN);
    var data = new KybCompanyData("12345678", "38501010001", R, List.of(person));

    var result = screener.screen(data);

    assertThat(result).isEmpty();
  }
}
