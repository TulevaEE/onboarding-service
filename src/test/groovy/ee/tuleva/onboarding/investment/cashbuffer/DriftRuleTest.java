package ee.tuleva.onboarding.investment.cashbuffer;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DriftRuleTest {

  private static final BigDecimal THRESHOLD = new BigDecimal("50000.00");
  private static final DriftRule RULE = new DriftRule(THRESHOLD, 2);

  @Test
  void aDivergenceWithinTheThresholdIsNoDriftAndResetsTheRun() {
    var drift = RULE.judge(new BigDecimal("-50000.00"), previous("-75000.00", 3));

    assertThat(drift).isEqualTo(new Drift(new BigDecimal("-50000.00"), THRESHOLD, false, 0, 2));
    assertThat(drift.sustained()).isFalse();
  }

  @Test
  void aFirstMonthBeyondTheThresholdIsAnEventNotYetASustainedDrift() {
    var drift = RULE.judge(new BigDecimal("50000.01"), Optional.empty());

    assertThat(drift).isEqualTo(new Drift(new BigDecimal("50000.01"), THRESHOLD, true, 1, 2));
    assertThat(drift.sustained()).isFalse();
  }

  @Test
  void theSecondConsecutiveMonthBeyondTheThresholdInEitherDirectionIsASustainedDrift() {
    var drift = RULE.judge(new BigDecimal("-86300.00"), previous("-60000.00", 1));

    assertThat(drift).isEqualTo(new Drift(new BigDecimal("-86300.00"), THRESHOLD, true, 2, 2));
    assertThat(drift.sustained()).isTrue();
  }

  @Test
  void aDriftThatKeepsGoingStaysSustainedAndKeepsCounting() {
    var drift = RULE.judge(new BigDecimal("75000.00"), previous("60000.00", 4));

    assertThat(drift).isEqualTo(new Drift(new BigDecimal("75000.00"), THRESHOLD, true, 5, 2));
    assertThat(drift.sustained()).isTrue();
  }

  @Test
  void aDriftThatChangesSidesIsANewDivergenceAndStartsItsRunAgain() {
    var drift = RULE.judge(new BigDecimal("-86300.00"), previous("75000.00", 4));

    assertThat(drift).isEqualTo(new Drift(new BigDecimal("-86300.00"), THRESHOLD, true, 1, 2));
    assertThat(drift.sustained()).isFalse();
  }

  @Test
  void aChangedThresholdStartsTheRunAgainSinceTheEarlierMonthsWereJudgedByAnotherRule() {
    var raisedThreshold = new BigDecimal("100000.00");

    var drift =
        new DriftRule(raisedThreshold, 2)
            .judge(new BigDecimal("110000.00"), previous("60000.00", 5));

    assertThat(drift)
        .isEqualTo(new Drift(new BigDecimal("110000.00"), raisedThreshold, true, 1, 2));
    assertThat(drift.sustained()).isFalse();
  }

  @Test
  void theSameThresholdStoredAtAnotherScaleKeepsTheRunGoing() {
    var sameThresholdUnscaled = new BigDecimal("50000");

    var drift =
        new DriftRule(sameThresholdUnscaled, 2)
            .judge(new BigDecimal("75000.00"), previous("60000.00", 4));

    assertThat(drift.consecutiveRuns()).isEqualTo(5);
  }

  private static Optional<Drift> previous(String divergence, int consecutiveRuns) {
    return Optional.of(new Drift(new BigDecimal(divergence), THRESHOLD, true, consecutiveRuns, 2));
  }
}
