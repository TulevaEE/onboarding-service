package ee.tuleva.onboarding.aml.risklevel;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DevRiskLevelsTest {

  private final DevRiskLevels riskLevels = new DevRiskLevels();

  @Test
  void holdsNoPersonsPayout() {
    assertThat(riskLevels.isHighRisk("38888888888")).isFalse();
  }

  @Test
  void holdsNoCompanysPayout() {
    assertThat(riskLevels.isHighRiskCompany("16001234")).isFalse();
  }
}
