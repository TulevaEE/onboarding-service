package ee.tuleva.onboarding.investment.config;

import static ee.tuleva.onboarding.investment.config.InvestmentParameter.ISSUE_REDEMPTION_PRICE_DEVIATION_THRESHOLD;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(InvestmentParameterRepository.class)
class IssueRedemptionPriceParameterSeedTest {

  private static final LocalDate FIRST_TKF100_ISSUE = LocalDate.of(2026, 2, 2);

  @Autowired private InvestmentParameterRepository repository;

  @Test
  void seedsTheTkf100IssueRedemptionPriceDeviationThresholdOfOperatsioonireegliPoint46() {
    assertThat(
            repository.findLatestValueIfPresent(
                ISSUE_REDEMPTION_PRICE_DEVIATION_THRESHOLD, TKF100, FIRST_TKF100_ISSUE))
        .hasValueSatisfying(
            value -> assertThat(value).isEqualByComparingTo(new BigDecimal("0.001")));
  }
}
