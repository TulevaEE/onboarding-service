package ee.tuleva.onboarding.investment.config;

import static ee.tuleva.onboarding.investment.config.InvestmentParameter.CASH_BUFFER_DRIFT_CONSECUTIVE_RUNS;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.CASH_BUFFER_DRIFT_THRESHOLD;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.CASH_BUFFER_INFLOW_CREDIT;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.CASH_BUFFER_INFLOW_PERCENTILE;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.CASH_BUFFER_OUTFLOW_PERCENTILE;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.CASH_BUFFER_SETTLEMENT_HORIZON_DAYS;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(InvestmentParameterRepository.class)
class CashBufferParameterSeedTest {

  private static final LocalDate FIRST_MONTHLY_REVIEW = LocalDate.of(2026, 10, 6);

  @Autowired private InvestmentParameterRepository repository;

  @Test
  void seedsThePercentilesOfTheBufferModel() {
    assertThat(repository.findLatestValue(CASH_BUFFER_OUTFLOW_PERCENTILE, FIRST_MONTHLY_REVIEW))
        .isEqualByComparingTo(new BigDecimal("0.95"));
    assertThat(repository.findLatestValue(CASH_BUFFER_INFLOW_PERCENTILE, FIRST_MONTHLY_REVIEW))
        .isEqualByComparingTo(new BigDecimal("0.20"));
  }

  @Test
  void seedsNoInflowOffsetUntilOneIsDecided() {
    assertThat(repository.findLatestValue(CASH_BUFFER_INFLOW_CREDIT, FIRST_MONTHLY_REVIEW))
        .isEqualByComparingTo(BigDecimal.ZERO);
  }

  @Test
  void seedsAnAbsoluteEurDriftThresholdThatMustHoldForSixConsecutiveRuns() {
    assertThat(repository.findLatestValue(CASH_BUFFER_DRIFT_THRESHOLD, FIRST_MONTHLY_REVIEW))
        .isEqualByComparingTo(new BigDecimal("50000"));
    assertThat(repository.findLatestValue(CASH_BUFFER_DRIFT_CONSECUTIVE_RUNS, FIRST_MONTHLY_REVIEW))
        .isEqualByComparingTo(new BigDecimal("6"));
  }

  @Test
  void seedsTheSettlementHorizonAsTheLongestCycleAFundSaleTakesToTurnIntoCash() {
    assertThat(
            repository.findLatestValue(CASH_BUFFER_SETTLEMENT_HORIZON_DAYS, FIRST_MONTHLY_REVIEW))
        .isEqualByComparingTo(new BigDecimal("4"));
  }
}
