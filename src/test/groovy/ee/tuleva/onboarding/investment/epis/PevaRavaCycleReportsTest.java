package ee.tuleva.onboarding.investment.epis;

import static ee.tuleva.onboarding.investment.report.ReportType.R17_PEVA;
import static ee.tuleva.onboarding.investment.report.ReportType.R21_RAVA;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PevaRavaCycleReportsTest {

  @Test
  void aCycleWithNeitherReportLinkedMissesBoth() {
    assertThat(new PevaRavaCycleReports(null, null).missingReports())
        .containsExactly(R17_PEVA, R21_RAVA);
  }

  @Test
  void aCycleWithOnlyR21LinkedMissesR17() {
    assertThat(new PevaRavaCycleReports(null, 2L).missingReports()).containsExactly(R17_PEVA);
  }

  @Test
  void aCycleWithOnlyR17LinkedMissesR21() {
    assertThat(new PevaRavaCycleReports(1L, null).missingReports()).containsExactly(R21_RAVA);
  }

  @Test
  void aCycleWithBothReportsLinkedMissesNothing() {
    assertThat(new PevaRavaCycleReports(1L, 2L).missingReports()).isEmpty();
  }
}
