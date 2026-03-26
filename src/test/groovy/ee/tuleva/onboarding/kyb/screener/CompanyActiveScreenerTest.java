package ee.tuleva.onboarding.kyb.screener;

import static ee.tuleva.onboarding.kyb.CompanyStatus.*;
import static ee.tuleva.onboarding.kyb.KybCheckType.COMPANY_ACTIVE;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.kyb.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class CompanyActiveScreenerTest {

  private final CompanyActiveScreener screener = new CompanyActiveScreener();

  @Test
  void registeredCompanyPasses() {
    var data = companyWithStatus(R);

    var result = screener.screen(data);

    assertThat(result).contains(new KybCheck(COMPANY_ACTIVE, true, Map.of("status", "R")));
  }

  @ParameterizedTest
  @EnumSource(
      value = CompanyStatus.class,
      names = {"L", "N", "K"})
  void nonActiveCompanyFails(CompanyStatus status) {
    var data = companyWithStatus(status);

    var result = screener.screen(data);

    assertThat(result)
        .contains(new KybCheck(COMPANY_ACTIVE, false, Map.of("status", status.name())));
  }

  private KybCompanyData companyWithStatus(CompanyStatus status) {
    return new KybCompanyData(
        new CompanyDto(new RegistryCode("12345678"), "Test OÜ", "62011", LegalForm.OÜ),
        new PersonalCode("38501010001"),
        status,
        List.of(),
        new SelfCertification(true, true, true));
  }
}
