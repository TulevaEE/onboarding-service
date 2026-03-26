package ee.tuleva.onboarding.kyb.screener;

import static ee.tuleva.onboarding.kyb.CompanyStatus.R;
import static ee.tuleva.onboarding.kyb.KybCheckType.COMPANY_STRUCTURE;
import static ee.tuleva.onboarding.kyb.KybKycStatus.COMPLETED;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.kyb.*;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class CompanyStructureScreenerTest {

  private final CompanyStructureScreener screener = new CompanyStructureScreener();

  @Test
  void singlePersonWithPersonalCodePasses() {
    var data = companyWith(List.of(identifiedPerson()));

    var result = screener.screen(data);

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().type()).isEqualTo(COMPANY_STRUCTURE);
    assertThat(result.getFirst().success()).isTrue();
  }

  @Test
  void twoPersonsWithPersonalCodesPasses() {
    var data =
        companyWith(
            List.of(
                identifiedPerson(new PersonalCode("38501010002")),
                identifiedPerson(new PersonalCode("49001010001"))));

    var result = screener.screen(data);

    assertThat(result.getFirst().success()).isTrue();
  }

  @Test
  void threePersonsFails() {
    var data =
        companyWith(
            List.of(
                identifiedPerson(new PersonalCode("38501010002")),
                identifiedPerson(new PersonalCode("49001010001")),
                identifiedPerson(new PersonalCode("37801010009"))));

    var result = screener.screen(data);

    assertThat(result.getFirst().success()).isFalse();
  }

  @Test
  void nullPersonalCodeFails() {
    var unidentified =
        new KybRelatedPerson(null, false, true, true, BigDecimal.valueOf(50), COMPLETED);
    var data = companyWith(List.of(identifiedPerson(), unidentified));

    var result = screener.screen(data);

    assertThat(result.getFirst().success()).isFalse();
  }

  @Test
  void metadataContainsRelatedPersonCount() {
    var data = companyWith(List.of(identifiedPerson()));

    var result = screener.screen(data);

    assertThat(result.getFirst().metadata())
        .containsEntry("relatedPersonCount", 1)
        .containsEntry("allIdentified", true);
  }

  private KybRelatedPerson identifiedPerson() {
    return identifiedPerson(new PersonalCode("38501010002"));
  }

  private KybRelatedPerson identifiedPerson(PersonalCode code) {
    return new KybRelatedPerson(code, true, true, true, BigDecimal.valueOf(100), COMPLETED);
  }

  private KybCompanyData companyWith(List<KybRelatedPerson> persons) {
    return new KybCompanyData(
        new CompanyDto(new RegistryCode("12345678"), "Test OÜ", "62011", LegalForm.OÜ),
        new PersonalCode("38501010002"),
        R,
        persons,
        new SelfCertification(true, true, true));
  }
}
