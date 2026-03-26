package ee.tuleva.onboarding.kyb.screener;

import static ee.tuleva.onboarding.kyb.CompanyStatus.R;
import static ee.tuleva.onboarding.kyb.KybCheckType.SELF_CERTIFICATION;
import static ee.tuleva.onboarding.kyb.KybKycStatus.COMPLETED;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.kyb.*;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class SelfCertificationScreenerTest {

  private final SelfCertificationScreener screener = new SelfCertificationScreener();

  @Test
  void allThreeConfirmedPasses() {
    var data = companyWithCertification(true, true, true);

    var results = screener.screen(data);

    assertThat(results).hasSize(1);
    assertThat(results.getFirst().type()).isEqualTo(SELF_CERTIFICATION);
    assertThat(results.getFirst().success()).isTrue();
  }

  @Test
  void operatesInEstoniaFalseFails() {
    var data = companyWithCertification(false, true, true);

    var results = screener.screen(data);

    assertThat(results.getFirst().success()).isFalse();
  }

  @Test
  void notSanctionedFalseFails() {
    var data = companyWithCertification(true, false, true);

    var results = screener.screen(data);

    assertThat(results.getFirst().success()).isFalse();
  }

  @Test
  void noHighRiskActivityFalseFails() {
    var data = companyWithCertification(true, true, false);

    var results = screener.screen(data);

    assertThat(results.getFirst().success()).isFalse();
  }

  @Test
  void nullCertificationFails() {
    var company = new CompanyDto(new RegistryCode("12345678"), "Test OÜ", "62011", LegalForm.OÜ);
    var person =
        new KybRelatedPerson(
            new PersonalCode("38501010001"), true, true, true, BigDecimal.valueOf(100), COMPLETED);
    var data =
        new KybCompanyData(company, new PersonalCode("38501010001"), R, List.of(person), null);

    var results = screener.screen(data);

    assertThat(results.getFirst().success()).isFalse();
  }

  @Test
  void metadataContainsCertificationValues() {
    var data = companyWithCertification(true, false, true);

    var results = screener.screen(data);

    var metadata = results.getFirst().metadata();
    assertThat(metadata)
        .containsEntry("operatesInEstonia", true)
        .containsEntry("notSanctioned", false)
        .containsEntry("noHighRiskActivity", true);
  }

  private KybCompanyData companyWithCertification(
      boolean operatesInEstonia, boolean notSanctioned, boolean noHighRiskActivity) {
    var company = new CompanyDto(new RegistryCode("12345678"), "Test OÜ", "62011", LegalForm.OÜ);
    var person =
        new KybRelatedPerson(
            new PersonalCode("38501010001"), true, true, true, BigDecimal.valueOf(100), COMPLETED);
    var cert = new SelfCertification(operatesInEstonia, notSanctioned, noHighRiskActivity);
    return new KybCompanyData(company, new PersonalCode("38501010001"), R, List.of(person), cert);
  }
}
