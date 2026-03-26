package ee.tuleva.onboarding.kyb.screener;

import static ee.tuleva.onboarding.kyb.CompanyStatus.R;
import static ee.tuleva.onboarding.kyb.KybCheckType.DUAL_MEMBER_OWNERSHIP;
import static ee.tuleva.onboarding.kyb.KybKycStatus.UNKNOWN;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.kyb.CompanyDto;
import ee.tuleva.onboarding.kyb.KybCompanyData;
import ee.tuleva.onboarding.kyb.KybRelatedPerson;
import ee.tuleva.onboarding.kyb.LegalForm;
import ee.tuleva.onboarding.kyb.PersonalCode;
import ee.tuleva.onboarding.kyb.RegistryCode;
import ee.tuleva.onboarding.kyb.SelfCertification;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class DualMemberOwnershipScreenerTest {

  private final DualMemberOwnershipScreener screener = new DualMemberOwnershipScreener();

  @Test
  void twoBoardMembersBothShareholdersAndBeneficialOwnersWithFullOwnershipPasses() {
    var person1 =
        new KybRelatedPerson(
            new PersonalCode("38501010001"), true, true, true, BigDecimal.valueOf(50), UNKNOWN);
    var person2 =
        new KybRelatedPerson(
            new PersonalCode("38501010002"), true, true, true, BigDecimal.valueOf(50), UNKNOWN);
    var data =
        new KybCompanyData(
            new CompanyDto(new RegistryCode("12345678"), "Test OÜ", "62011", LegalForm.OÜ),
            new PersonalCode("38501010001"),
            R,
            List.of(person1, person2),
            new SelfCertification(true, true, true));

    var result = screener.screen(data);

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().type()).isEqualTo(DUAL_MEMBER_OWNERSHIP);
    assertThat(result.getFirst().success()).isTrue();
  }

  @Test
  void twoBoardMembersWithTotalOwnershipLessThan100Fails() {
    var person1 =
        new KybRelatedPerson(
            new PersonalCode("38501010001"), true, true, true, BigDecimal.valueOf(30), UNKNOWN);
    var person2 =
        new KybRelatedPerson(
            new PersonalCode("38501010002"), true, true, true, BigDecimal.valueOf(30), UNKNOWN);
    var data =
        new KybCompanyData(
            new CompanyDto(new RegistryCode("12345678"), "Test OÜ", "62011", LegalForm.OÜ),
            new PersonalCode("38501010001"),
            R,
            List.of(person1, person2),
            new SelfCertification(true, true, true));

    var result = screener.screen(data);

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().type()).isEqualTo(DUAL_MEMBER_OWNERSHIP);
    assertThat(result.getFirst().success()).isFalse();
  }

  @Test
  void twoBoardMembersWhereOneIsNotShareholderFails() {
    var person1 =
        new KybRelatedPerson(
            new PersonalCode("38501010001"), true, true, true, BigDecimal.valueOf(100), UNKNOWN);
    var person2 =
        new KybRelatedPerson(
            new PersonalCode("38501010002"), true, false, false, BigDecimal.ZERO, UNKNOWN);
    var data =
        new KybCompanyData(
            new CompanyDto(new RegistryCode("12345678"), "Test OÜ", "62011", LegalForm.OÜ),
            new PersonalCode("38501010001"),
            R,
            List.of(person1, person2),
            new SelfCertification(true, true, true));

    var result = screener.screen(data);

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().type()).isEqualTo(DUAL_MEMBER_OWNERSHIP);
    assertThat(result.getFirst().success()).isFalse();
  }

  @Test
  void doesNotApplyWhenOnlyOneBoardMemberOutOfTwoPersons() {
    var person1 =
        new KybRelatedPerson(
            new PersonalCode("38501010001"), true, true, true, BigDecimal.valueOf(50), UNKNOWN);
    var person2 =
        new KybRelatedPerson(
            new PersonalCode("38501010002"), false, true, true, BigDecimal.valueOf(50), UNKNOWN);
    var data =
        new KybCompanyData(
            new CompanyDto(new RegistryCode("12345678"), "Test OÜ", "62011", LegalForm.OÜ),
            new PersonalCode("38501010001"),
            R,
            List.of(person1, person2),
            new SelfCertification(true, true, true));

    var result = screener.screen(data);

    assertThat(result).isEmpty();
  }

  @Test
  void doesNotApplyWhenOnlyOneRelatedPerson() {
    var person =
        new KybRelatedPerson(
            new PersonalCode("38501010001"), true, true, true, BigDecimal.valueOf(100), UNKNOWN);
    var data =
        new KybCompanyData(
            new CompanyDto(new RegistryCode("12345678"), "Test OÜ", "62011", LegalForm.OÜ),
            new PersonalCode("38501010001"),
            R,
            List.of(person),
            new SelfCertification(true, true, true));

    var result = screener.screen(data);

    assertThat(result).isEmpty();
  }
}
