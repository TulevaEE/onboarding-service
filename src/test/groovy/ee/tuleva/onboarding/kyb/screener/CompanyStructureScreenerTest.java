package ee.tuleva.onboarding.kyb.screener;

import static ee.tuleva.onboarding.kyb.KybCheckType.COMPANY_STRUCTURE;
import static ee.tuleva.onboarding.kyb.KybKycStatus.COMPLETED;
import static ee.tuleva.onboarding.kyb.KybTestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.kyb.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CompanyStructureScreenerTest {

  private final CompanyStructureScreener screener = new CompanyStructureScreener();

  @Test
  void singlePersonWithPersonalCodePasses() {
    var data = companyWith(identifiedPerson());

    var result = screener.screen(data);

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().type()).isEqualTo(COMPANY_STRUCTURE);
    assertThat(result.getFirst().success()).isTrue();
  }

  @Test
  void twoPersonsWithPersonalCodesPasses() {
    var data =
        companyWith(
            identifiedPerson(new PersonalCode("38501010002")),
            identifiedPerson(new PersonalCode("49001010001")));

    var result = screener.screen(data);

    assertThat(result.getFirst().success()).isTrue();
  }

  @Test
  void threePersonsFails() {
    var data =
        companyWith(
            identifiedPerson(new PersonalCode("38501010002")),
            identifiedPerson(new PersonalCode("49001010001")),
            identifiedPerson(new PersonalCode("37801010009")));

    var result = screener.screen(data);

    assertThat(result.getFirst().success()).isFalse();
  }

  @Test
  void nullPersonalCodeFails() {
    var unidentified =
        kybPerson()
            .personalCode(null)
            .shareholder(true)
            .beneficialOwner(true)
            .ownershipPercent(BigDecimal.valueOf(50))
            .kycStatus(COMPLETED)
            .build();
    var data = companyWith(identifiedPerson(), unidentified);

    var result = screener.screen(data);

    assertThat(result.getFirst().success()).isFalse();
  }

  @Test
  void metadataContainsRelatedPersonCount() {
    var data = companyWith(identifiedPerson());

    var result = screener.screen(data);

    assertThat(result.getFirst().metadata())
        .containsEntry("relatedPersonCount", 1)
        .containsEntry("allIdentified", true);
  }

  @Test
  void legalEntityRelatedPersonFails() {
    var legalEntityOwner =
        kybPerson("90000002")
            .naturalPerson(false)
            .shareholder(true)
            .beneficialOwner(true)
            .ownershipPercent(BigDecimal.valueOf(100))
            .kycStatus(COMPLETED)
            .build();
    var data = companyWith(legalEntityOwner);

    var result = screener.screen(data);

    assertThat(result.getFirst().success()).isFalse();
  }

  @Test
  void twoRelatedPersonsWithNoBoardMemberFails() {
    var owner1 =
        kybPerson("38501010002")
            .shareholder(true)
            .beneficialOwner(true)
            .ownershipPercent(BigDecimal.valueOf(50))
            .kycStatus(COMPLETED)
            .build();
    var owner2 =
        kybPerson("49001010001")
            .shareholder(true)
            .beneficialOwner(true)
            .ownershipPercent(BigDecimal.valueOf(50))
            .kycStatus(COMPLETED)
            .build();
    var data = companyWith(owner1, owner2);

    var result = screener.screen(data);

    assertThat(result.getFirst().success()).isFalse();
  }

  private KybRelatedPerson identifiedPerson() {
    return identifiedPerson(new PersonalCode("38501010002"));
  }

  private KybRelatedPerson identifiedPerson(PersonalCode code) {
    return boardMemberOwner(code, 100.0).kycStatus(COMPLETED).build();
  }
}
