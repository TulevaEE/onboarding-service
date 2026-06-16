package ee.tuleva.onboarding.kyb.screener;

import static ee.tuleva.onboarding.kyb.KybCheckType.SOLE_MEMBER_OWNERSHIP;
import static ee.tuleva.onboarding.kyb.KybTestFixtures.boardMemberOwner;
import static ee.tuleva.onboarding.kyb.KybTestFixtures.companyWith;
import static ee.tuleva.onboarding.kyb.KybTestFixtures.kybPerson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import ee.tuleva.onboarding.kyb.KybCheck;
import ee.tuleva.onboarding.kyb.PersonalCode;
import java.math.BigDecimal;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SoleMemberOwnershipScreenerTest {

  private final SoleMemberOwnershipScreener screener = new SoleMemberOwnershipScreener();

  @ParameterizedTest
  @MethodSource("singlePersonScenarios")
  void singlePersonOwnership(
      boolean board, boolean share, boolean beneficial, BigDecimal ownership, boolean expected) {
    var person =
        kybPerson("38501010001")
            .boardMember(board)
            .shareholder(share)
            .beneficialOwner(beneficial)
            .ownershipPercent(ownership)
            .build();
    var data = companyWith(person);

    var result = screener.screen(data);

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().type()).isEqualTo(SOLE_MEMBER_OWNERSHIP);
    assertThat(result.getFirst().success()).isEqualTo(expected);
  }

  static Stream<Arguments> singlePersonScenarios() {
    return Stream.of(
        Arguments.of(true, true, true, BigDecimal.valueOf(100), true),
        Arguments.of(true, true, false, BigDecimal.valueOf(100), false),
        Arguments.of(true, false, true, BigDecimal.ZERO, false),
        Arguments.of(true, true, true, BigDecimal.valueOf(50), false),
        Arguments.of(false, true, true, BigDecimal.valueOf(100), false));
  }

  @Test
  void handlesNullPersonalCode() {
    var person = boardMemberOwner((PersonalCode) null, 100.0).build();
    var data = companyWith(person);

    var result = screener.screen(data);

    assertThat(result)
        .extracting(KybCheck::type, KybCheck::success)
        .containsExactly(tuple(SOLE_MEMBER_OWNERSHIP, true));
    assertThat(result.getFirst().metadata()).doesNotContainKey("personalCode");
  }

  @Test
  void doesNotApplyWhenMultipleRelatedPersons() {
    var person1 = boardMemberOwner("38501010001", 50.0).build();
    var person2 = boardMemberOwner("38501010002", 50.0).build();
    var data = companyWith(person1, person2);

    var result = screener.screen(data);

    assertThat(result).isEmpty();
  }
}
