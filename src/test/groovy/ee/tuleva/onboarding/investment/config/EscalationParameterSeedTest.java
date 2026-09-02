package ee.tuleva.onboarding.investment.config;

import static ee.tuleva.onboarding.investment.config.InvestmentParameter.ESCALATION_LOOKBACK_DAYS;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.ESCALATION_NET_TD_THRESHOLD;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.ESCALATION_THRESHOLD_DAYS;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(InvestmentParameterRepository.class)
class EscalationParameterSeedTest {

  private static final LocalDate AFTER_LATEST_SEED = LocalDate.of(2030, 1, 1);

  @Autowired private InvestmentParameterRepository repository;

  @Test
  void escalationNetTdThresholdDoesNotSuppressAStreakThatBreachesDaily() {
    BigDecimal netTdThreshold =
        repository.findLatestValue(ESCALATION_NET_TD_THRESHOLD, AFTER_LATEST_SEED);

    assertThat(netTdThreshold).isEqualByComparingTo(new BigDecimal("0.001"));
  }

  // Sisekord nr 4 p 11.7 escalates when the breach "püsib enam kui kolm (3) tööpäeva" - persists
  // MORE THAN three working days - so the fourth consecutive breach day is the first that
  // escalates, not the third.
  @Test
  void escalationStreakLengthMatchesTheInternalRule() {
    BigDecimal thresholdDays =
        repository.findLatestValue(ESCALATION_THRESHOLD_DAYS, AFTER_LATEST_SEED);

    assertThat(thresholdDays).isEqualByComparingTo(new BigDecimal("4"));
  }

  @Test
  void escalationLookbackCoversMoreDaysThanTheStreakItMustDetect() {
    BigDecimal lookbackDays =
        repository.findLatestValue(ESCALATION_LOOKBACK_DAYS, AFTER_LATEST_SEED);
    BigDecimal thresholdDays =
        repository.findLatestValue(ESCALATION_THRESHOLD_DAYS, AFTER_LATEST_SEED);

    assertThat(lookbackDays).isGreaterThan(thresholdDays);
  }
}
