package ee.tuleva.onboarding.aml.risklevel;

import static ee.tuleva.onboarding.aml.PersonRiskLevel.Level.NOT_HIGH;
import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.aml.PersonRiskLevel;
import java.time.Clock;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class DevRiskLevelsTest {

  private static final Instant NOW = Instant.parse("2026-11-16T10:00:00Z");

  private final DevRiskLevels riskLevels = new DevRiskLevels(Clock.fixed(NOW, UTC));

  @Test
  void holdsNoPersonsPayoutAndCountsTheLevelAsAssessedNowSoAnEarlierPepHitIsIncluded() {
    assertThat(riskLevels.riskLevel("38888888888")).isEqualTo(new PersonRiskLevel(NOT_HIGH, NOW));
  }

  @Test
  void holdsNoCompanysPayout() {
    assertThat(riskLevels.isHighRiskCompany("16001234")).isFalse();
  }
}
