package ee.tuleva.onboarding.kyb.screener;

import static ee.tuleva.onboarding.kyb.CompanyStatus.R;
import static ee.tuleva.onboarding.kyb.KybCheckType.SOLE_MEMBER_OWNERSHIP;
import static ee.tuleva.onboarding.kyb.KybKycStatus.UNKNOWN;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.kyb.CompanyDto;
import ee.tuleva.onboarding.kyb.KybCompanyData;
import ee.tuleva.onboarding.kyb.KybRelatedPerson;
import ee.tuleva.onboarding.kyb.PersonalCode;
import ee.tuleva.onboarding.kyb.RegistryCode;
import ee.tuleva.onboarding.kyb.SelfCertification;
import java.math.BigDecimal;
import java.util.List;
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
        new KybRelatedPerson(
            new PersonalCode("38501010001"), board, share, beneficial, ownership, UNKNOWN);
    var data =
        new KybCompanyData(
            new CompanyDto(new RegistryCode("12345678"), "Test OÜ", "62011"),
            new PersonalCode("38501010001"),
            R,
            List.of(person),
            new SelfCertification(true, true, true));

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
  void doesNotApplyWhenMultipleRelatedPersons() {
    var person1 =
        new KybRelatedPerson(
            new PersonalCode("38501010001"), true, true, true, BigDecimal.valueOf(50), UNKNOWN);
    var person2 =
        new KybRelatedPerson(
            new PersonalCode("38501010002"), true, true, true, BigDecimal.valueOf(50), UNKNOWN);
    var data =
        new KybCompanyData(
            new CompanyDto(new RegistryCode("12345678"), "Test OÜ", "62011"),
            new PersonalCode("38501010001"),
            R,
            List.of(person1, person2),
            new SelfCertification(true, true, true));

    var result = screener.screen(data);

    assertThat(result).isEmpty();
  }
}
