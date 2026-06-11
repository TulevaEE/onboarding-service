package ee.tuleva.onboarding.kyb.screener;

import static ee.tuleva.onboarding.kyb.KybCheckType.OWNER_CHANGED;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.kyb.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OwnerChangeScreenerTest {

  private final OwnerChangeScreener screener = new OwnerChangeScreener();

  @Test
  void failsWhenOwnerChangedBeforeOnboarding() {
    var result = screener.screen(company(true));

    assertThat(result)
        .containsExactly(
            new KybCheck(OWNER_CHANGED, false, Map.of("ownerChangedBeforeOnboarding", true)));
  }

  @Test
  void passesWhenOwnerNeverChanged() {
    var result = screener.screen(company(false));

    assertThat(result)
        .containsExactly(
            new KybCheck(OWNER_CHANGED, true, Map.of("ownerChangedBeforeOnboarding", false)));
  }

  private static KybCompanyData company(boolean ownerChangedBeforeOnboarding) {
    return new KybCompanyData(
        new CompanyDto(new RegistryCode("12345678"), "Test OÜ", "62011", LegalForm.OÜ),
        new PersonalCode("38501010001"),
        CompanyStatus.R,
        List.of(),
        new SelfCertification(true, true, true),
        "EE",
        "Harju maakond, Tallinn, Pärnu mnt 1",
        null,
        ownerChangedBeforeOnboarding);
  }
}
