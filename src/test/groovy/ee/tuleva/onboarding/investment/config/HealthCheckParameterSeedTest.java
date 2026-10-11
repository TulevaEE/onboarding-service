package ee.tuleva.onboarding.investment.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(InvestmentParameterRepository.class)
class HealthCheckParameterSeedTest {

  // FundPositionImportJob re-runs the health check over a 14-day lookback, and findLatestValue
  // throws when nothing is effective yet, so a threshold seeded at its own deploy date breaks
  // every nav date behind it. Health check thresholds are seeded before the history they govern.
  private static final LocalDate BEFORE_ANY_HEALTH_CHECKED_NAV_DATE = LocalDate.of(2026, 1, 1);

  @Autowired private InvestmentParameterRepository repository;

  @ParameterizedTest
  @EnumSource(
      value = InvestmentParameter.class,
      names = {"NAV_FLOW_CONSISTENCY_THRESHOLD", "NAV_IMPACT_VOLUME_THRESHOLD"})
  void healthCheckThresholdsCoverEveryNavDateTheImportLookbackReaches(
      InvestmentParameter parameter) {
    assertThat(repository.findLatestValue(parameter, BEFORE_ANY_HEALTH_CHECKED_NAV_DATE))
        .isPositive();
  }
}
