package ee.tuleva.onboarding.kyb.screener;

import static ee.tuleva.onboarding.kyb.CompanyStatus.R;
import static ee.tuleva.onboarding.kyb.KybCheckType.SHAREHOLDER_ELIGIBILITY;
import static ee.tuleva.onboarding.kyb.KybKycStatus.COMPLETED;
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

class ShareholderEligibilityScreenerTest {

  private final ShareholderEligibilityScreener screener = new ShareholderEligibilityScreener();

  @Test
  void soleShareholderWhoIsBoardMemberAndBeneficialOwnerWithFullOwnershipPasses() {
    var data = companyWith(List.of(shareholder("38501010002", true, true, hundred())));

    assertPass(screener.screen(data));
  }

  @Test
  void soleOwnerDirectorPlusNonOwnerDirectorPasses() {
    // ProAssets shape: one 100% owner-director plus a second board member who owns nothing.
    var data =
        companyWith(
            List.of(
                shareholder("38501010002", true, true, hundred()),
                nonOwnerDirector("49001010001")));

    assertPass(screener.screen(data));
  }

  @Test
  void soleOwnerDirectorPlusMultipleNonOwnerDirectorsPasses() {
    // This screener only evaluates shareholders; the ≤2 related-person cap is enforced
    // separately by CompanyStructureScreener, so a third director is rejected there, not here.
    var data =
        companyWith(
            List.of(
                shareholder("38501010002", true, true, hundred()),
                nonOwnerDirector("49001010001"),
                nonOwnerDirector("37801010009")));

    assertPass(screener.screen(data));
  }

  @Test
  void twoShareholdersBothBoardMembersAndBeneficialOwnersSummingTo100Passes() {
    var data =
        companyWith(
            List.of(
                shareholder("38501010002", true, true, BigDecimal.valueOf(50)),
                shareholder("49001010001", true, true, BigDecimal.valueOf(50))));

    assertPass(screener.screen(data));
  }

  @Test
  void twoShareholdersWhereOnlyOneIsBoardMemberAndBeneficialOwnerPasses() {
    // "üks või mõlemad": the second shareholder need not be a director or beneficial owner.
    var data =
        companyWith(
            List.of(
                shareholder("38501010002", true, true, BigDecimal.valueOf(60)),
                shareholder("49001010001", false, false, BigDecimal.valueOf(40))));

    assertPass(screener.screen(data));
  }

  @Test
  void twoShareholdersWhereNeitherIsBoardMemberAndBeneficialOwnerFails() {
    var data =
        companyWith(
            List.of(
                shareholder("38501010002", false, false, BigDecimal.valueOf(60)),
                shareholder("49001010001", false, false, BigDecimal.valueOf(40))));

    assertFail(screener.screen(data));
  }

  @Test
  void soleShareholderWhoIsNotABoardMemberFails() {
    var data = companyWith(List.of(shareholder("38501010002", false, true, hundred())));

    assertFail(screener.screen(data));
  }

  @Test
  void threeShareholdersFails() {
    var data =
        companyWith(
            List.of(
                shareholder("38501010002", true, true, BigDecimal.valueOf(40)),
                shareholder("49001010001", false, false, BigDecimal.valueOf(40)),
                shareholder("37801010009", false, false, BigDecimal.valueOf(20))));

    assertFail(screener.screen(data));
  }

  @Test
  void unidentifiedShareholderFails() {
    var unidentified = new KybRelatedPerson(null, false, true, true, hundred(), COMPLETED);
    var data =
        companyWith(List.of(shareholder("38501010002", true, true, BigDecimal.ZERO), unidentified));

    assertFail(screener.screen(data));
  }

  @Test
  void ownershipNotSummingTo100Fails() {
    var data = companyWith(List.of(shareholder("38501010002", true, true, BigDecimal.valueOf(60))));

    assertFail(screener.screen(data));
  }

  @Test
  void legacySoleBoardMemberShapeWithIncompleteOwnershipNowFails() {
    // Regression marker: under the old SoleBoardMemberIsOwnerScreener (no sum check) this passed.
    // The unified screener applies sum==100 to every shape, so an unaccounted 40% now fails.
    var data =
        companyWith(
            List.of(
                shareholder("38501010002", true, true, BigDecimal.valueOf(60)),
                shareholder("49001010001", false, false, BigDecimal.ZERO)));

    assertFail(screener.screen(data));
  }

  @Test
  void companyWithNoShareholdersFails() {
    var data = companyWith(List.of(nonOwnerDirector("38501010002")));

    assertFail(screener.screen(data));
  }

  @Test
  void metadataReportsShareholderCountAndTotalOwnership() {
    var data =
        companyWith(
            List.of(
                shareholder("38501010002", true, true, BigDecimal.valueOf(70)),
                shareholder("49001010001", false, false, BigDecimal.valueOf(30))));

    var result = screener.screen(data);

    assertThat(result.getFirst().metadata())
        .containsEntry("shareholderCount", 2)
        .containsEntry("totalOwnership", hundred())
        .containsEntry("allIdentified", true)
        .containsEntry("hasOwnerDirector", true);
  }

  private void assertPass(List<ee.tuleva.onboarding.kyb.KybCheck> result) {
    assertThat(result).hasSize(1);
    assertThat(result.getFirst().type()).isEqualTo(SHAREHOLDER_ELIGIBILITY);
    assertThat(result.getFirst().success()).isTrue();
  }

  private void assertFail(List<ee.tuleva.onboarding.kyb.KybCheck> result) {
    assertThat(result).hasSize(1);
    assertThat(result.getFirst().type()).isEqualTo(SHAREHOLDER_ELIGIBILITY);
    assertThat(result.getFirst().success()).isFalse();
  }

  private KybRelatedPerson shareholder(
      String code, boolean boardMember, boolean beneficialOwner, BigDecimal ownershipPercent) {
    return new KybRelatedPerson(
        new PersonalCode(code), boardMember, true, beneficialOwner, ownershipPercent, COMPLETED);
  }

  private KybRelatedPerson nonOwnerDirector(String code) {
    return new KybRelatedPerson(
        new PersonalCode(code), true, false, false, BigDecimal.ZERO, COMPLETED);
  }

  private BigDecimal hundred() {
    return BigDecimal.valueOf(100);
  }

  private KybCompanyData companyWith(List<KybRelatedPerson> persons) {
    return new KybCompanyData(
        new CompanyDto(new RegistryCode("12345678"), "Test OÜ", "62011", LegalForm.OÜ),
        new PersonalCode("38501010002"),
        R,
        persons,
        new SelfCertification(true, true, true),
        "EE",
        "Harju maakond, Tallinn, Pärnu mnt 1",
        null);
  }
}
