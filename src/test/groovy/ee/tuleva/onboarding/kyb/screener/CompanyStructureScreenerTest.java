package ee.tuleva.onboarding.kyb.screener;

import static ee.tuleva.onboarding.kyb.KybCheckType.COMPANY_STRUCTURE;
import static ee.tuleva.onboarding.kyb.KybKycStatus.COMPLETED;
import static ee.tuleva.onboarding.kyb.KybTestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import ee.tuleva.onboarding.kyb.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CompanyStructureScreenerTest {

  private final CompanyStructureScreener screener = new CompanyStructureScreener();

  @Test
  void singlePersonWithPersonalCodePasses() {
    var data = companyWith(identifiedPerson());

    var result = screener.screen(data);

    assertThat(result)
        .extracting(KybCheck::type, KybCheck::success)
        .containsExactly(tuple(COMPANY_STRUCTURE, true));
  }

  @Test
  void twoPersonsWithPersonalCodesPasses() {
    var data = companyWith(identifiedPerson("38501010002"), identifiedPerson("49001010001"));

    var result = screener.screen(data);

    assertThat(result.getFirst().success()).isTrue();
  }

  @Test
  void threePersonsFails() {
    var data =
        companyWith(
            identifiedPerson("38501010002"),
            identifiedPerson("49001010001"),
            identifiedPerson("37801010009"));

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
    var owner1 = shareholderOwner("38501010002", 50.0).build();
    var owner2 = shareholderOwner("49001010001", 50.0).build();
    var data = companyWith(owner1, owner2);

    var result = screener.screen(data);

    assertThat(result.getFirst().success()).isFalse();
  }

  private KybRelatedPerson identifiedPerson() {
    return identifiedPerson("38501010002");
  }

  private KybRelatedPerson identifiedPerson(String code) {
    return boardMemberOwner(code, 100.0).build();
  }
}
