package ee.tuleva.onboarding.investment.check.fee;

import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.FAIL;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.INFO;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.NOT_RUN;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.PASS;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.WARNING;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class FeeCheckSeverityTest {

  @Test
  void ranksADifferenceWeHaveExplainedBelowADayWeCouldNotLookAt() {
    assertThat(List.of(FeeCheckSeverity.values()))
        .containsExactly(PASS, INFO, NOT_RUN, WARNING, FAIL);
  }

  @Test
  void takesTheLoudestSeverityOfARun() {
    assertThat(List.of(PASS, INFO, NOT_RUN).stream().max(Enum::compareTo)).contains(NOT_RUN);
    assertThat(List.of(INFO, WARNING).stream().max(Enum::compareTo)).contains(WARNING);
    assertThat(List.of(NOT_RUN, FAIL).stream().max(Enum::compareTo)).contains(FAIL);
  }
}
