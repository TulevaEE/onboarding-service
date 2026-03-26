package ee.tuleva.onboarding.kyb.screener;

import static ee.tuleva.onboarding.kyb.CompanyStatus.R;
import static ee.tuleva.onboarding.kyb.KybCheckType.COMPANY_LEGAL_FORM;
import static ee.tuleva.onboarding.kyb.LegalForm.*;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.kyb.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class CompanyLegalFormScreenerTest {

  private final CompanyLegalFormScreener screener = new CompanyLegalFormScreener();

  @Test
  void olesAccepted() {
    var data = companyWithLegalForm(OÜ);

    var result = screener.screen(data);

    assertThat(result).contains(new KybCheck(COMPANY_LEGAL_FORM, true, Map.of("legalForm", "OÜ")));
  }

  @ParameterizedTest
  @EnumSource(
      value = LegalForm.class,
      names = {"OÜ"},
      mode = EnumSource.Mode.EXCLUDE)
  void otherLegalFormsFail(LegalForm legalForm) {
    var data = companyWithLegalForm(legalForm);

    var result = screener.screen(data);

    assertThat(result)
        .contains(new KybCheck(COMPANY_LEGAL_FORM, false, Map.of("legalForm", legalForm.name())));
  }

  @Test
  void nullLegalFormFails() {
    var data = companyWithLegalForm(null);

    var result = screener.screen(data);

    assertThat(result)
        .contains(new KybCheck(COMPANY_LEGAL_FORM, false, Map.of("legalForm", "null")));
  }

  private KybCompanyData companyWithLegalForm(LegalForm legalForm) {
    return new KybCompanyData(
        new CompanyDto(new RegistryCode("12345678"), "Test OÜ", "62011", legalForm),
        new PersonalCode("38501010001"),
        R,
        List.of(),
        new SelfCertification(true, true, true));
  }
}
