package ee.tuleva.onboarding.investment.cashbuffer;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class DriftRuleTest {

  private static final BigDecimal THRESHOLD = new BigDecimal("50000.00");
  private static final DriftRule RULE = new DriftRule(THRESHOLD, 2);

  @Test
  void aDivergenceWithinTheThresholdIsNoDriftAndResetsTheRun() {
    var drift = RULE.judge(new BigDecimal("-50000.00"), 3);

    assertThat(drift).isEqualTo(new Drift(new BigDecimal("-50000.00"), THRESHOLD, false, 0, 2));
    assertThat(drift.sustained()).isFalse();
  }

  @Test
  void aFirstMonthBeyondTheThresholdIsAnEventNotYetASustainedDrift() {
    var drift = RULE.judge(new BigDecimal("50000.01"), 0);

    assertThat(drift).isEqualTo(new Drift(new BigDecimal("50000.01"), THRESHOLD, true, 1, 2));
    assertThat(drift.sustained()).isFalse();
  }

  @Test
  void theSecondConsecutiveMonthBeyondTheThresholdInEitherDirectionIsASustainedDrift() {
    var drift = RULE.judge(new BigDecimal("-86300.00"), 1);

    assertThat(drift).isEqualTo(new Drift(new BigDecimal("-86300.00"), THRESHOLD, true, 2, 2));
    assertThat(drift.sustained()).isTrue();
  }

  @Test
  void aDriftThatKeepsGoingStaysSustainedAndKeepsCounting() {
    var drift = RULE.judge(new BigDecimal("75000.00"), 4);

    assertThat(drift.consecutiveRuns()).isEqualTo(5);
    assertThat(drift.sustained()).isTrue();
  }
}
