package ee.tuleva.onboarding.kyb.screener;

import static ee.tuleva.onboarding.kyb.CompanyStatus.R;
import static ee.tuleva.onboarding.kyb.KybCheckType.HIGH_RISK_NACE;
import static ee.tuleva.onboarding.kyb.KybKycStatus.COMPLETED;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.kyb.*;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CompanyNaceScreenerTest {

  private final CompanyNaceScreener screener = new CompanyNaceScreener();

  @Test
  void safeNaceCodePasses() {
    var data = companyWithNace("62011");

    var results = screener.screen(data);

    assertThat(results).hasSize(1);
    assertThat(results.getFirst().type()).isEqualTo(HIGH_RISK_NACE);
    assertThat(results.getFirst().success()).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"64321", "66199", "92001", "47791"})
  void highRiskNaceCodeFails(String naceCode) {
    var data = companyWithNace(naceCode);

    var results = screener.screen(data);

    assertThat(results).hasSize(1);
    assertThat(results.getFirst().type()).isEqualTo(HIGH_RISK_NACE);
    assertThat(results.getFirst().success()).isFalse();
  }

  @Test
  void nullNaceCodeFails() {
    var data = companyWithNace(null);

    var results = screener.screen(data);

    assertThat(results).hasSize(1);
    assertThat(results.getFirst().success()).isFalse();
    assertThat(results.getFirst().metadata()).containsEntry("naceCode", "unknown");
  }

  @Test
  void metadataContainsNaceCode() {
    var data = companyWithNace("92001");

    var results = screener.screen(data);

    assertThat(results.getFirst().metadata()).containsEntry("naceCode", "92001");
  }

  private KybCompanyData companyWithNace(String naceCode) {
    var company = new CompanyDto(new RegistryCode("12345678"), "Test OÜ", naceCode, LegalForm.OÜ);
    var person =
        new KybRelatedPerson(
            new PersonalCode("38501010001"), true, true, true, BigDecimal.valueOf(100), COMPLETED);
    return new KybCompanyData(
        company,
        new PersonalCode("38501010001"),
        R,
        List.of(person),
        new SelfCertification(true, true, true));
  }
}
