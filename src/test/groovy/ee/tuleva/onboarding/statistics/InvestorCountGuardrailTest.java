package ee.tuleva.onboarding.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

class InvestorCountGuardrailTest {

  private final InvestorCountGuardrailProperties properties =
      new InvestorCountGuardrailProperties(80000, 95000, 5.0);
  private final InvestorCountGuardrail guardrail = new InvestorCountGuardrail(properties);

  @Test
  void noViolations_whenInBoundsAndChangeSmall() {
    assertThat(guardrail.findViolations(85224, OptionalLong.of(85000))).isEmpty();
  }

  @Test
  void flagsCountBelowLowerBound() {
    assertThat(guardrail.findViolations(79999, OptionalLong.of(80000)))
        .hasSize(1)
        .allMatch(violation -> violation.contains("out of expected bounds"));
  }

  @Test
  void flagsCountAboveUpperBound() {
    assertThat(guardrail.findViolations(95001, OptionalLong.of(94000)))
        .hasSize(1)
        .allMatch(violation -> violation.contains("out of expected bounds"));
  }

  @Test
  void flagsTooLargeChangeVsPreviousPeriod() {
    assertThat(guardrail.findViolations(93500, OptionalLong.of(85000)))
        .hasSize(1)
        .allMatch(violation -> violation.contains("changed by"));
  }

  @Test
  void onlyChecksBounds_whenNoPreviousPeriod() {
    assertThat(guardrail.findViolations(85000, OptionalLong.empty())).isEmpty();
  }

  @Test
  void flagsJumpFromZeroPreviousPeriod() {
    assertThat(guardrail.findViolations(85000, OptionalLong.of(0)))
        .hasSize(1)
        .allMatch(violation -> violation.contains("changed from 0"));
  }
}
