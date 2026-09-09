package ee.tuleva.onboarding.investment.config;

import static ee.tuleva.onboarding.investment.config.InvestmentParameter.REDEMPTION_LIQUIDITY_WARNING_SHARE_OF_AUM;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.REDEMPTION_PAYOUT_WARNING_THRESHOLD;
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
class RedemptionAlertParameterSeedTest {

  // RedemptionAlertJob asks as of the run date and skips when nothing is effective yet, so
  // thresholds seeded at their own deploy date would silence every alert the job has already
  // raised. Both numbers are seeded from the day the unified job started applying them.
  private static final LocalDate FIRST_UNIFIED_ALERT_RUN = LocalDate.of(2026, 5, 19);

  @Autowired private InvestmentParameterRepository repository;

  @Test
  void seedsTheTkf100PayoutWarningThresholdTheJobHasBeenApplyingSince() {
    assertThat(
            repository.findLatestValueIfPresent(
                REDEMPTION_PAYOUT_WARNING_THRESHOLD, TKF100, FIRST_UNIFIED_ALERT_RUN))
        .hasValueSatisfying(
            value -> assertThat(value).isEqualByComparingTo(new BigDecimal("40000")));
  }

  @Test
  void seedsTheTkf100LiquidityWarningShareOfAum() {
    assertThat(
            repository.findLatestValueIfPresent(
                REDEMPTION_LIQUIDITY_WARNING_SHARE_OF_AUM, TKF100, FIRST_UNIFIED_ALERT_RUN))
        .hasValueSatisfying(
            value -> assertThat(value).isEqualByComparingTo(new BigDecimal("0.01")));
  }
}
