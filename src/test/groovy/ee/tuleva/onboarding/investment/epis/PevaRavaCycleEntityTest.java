package ee.tuleva.onboarding.investment.epis;

import static ee.tuleva.onboarding.investment.report.ReportType.R17_PEVA;
import static ee.tuleva.onboarding.investment.report.ReportType.R21_RAVA;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PevaRavaCycleEntityTest {

  @Test
  void aCycleWithNeitherReportLinkedMissesBoth() {
    assertThat(PevaRavaCycleEntity.builder().build().missingReports())
        .containsExactly(R17_PEVA, R21_RAVA);
  }

  @Test
  void aCycleWithOnlyR21LinkedMissesR17() {
    assertThat(PevaRavaCycleEntity.builder().r21ReportId(2L).build().missingReports())
        .containsExactly(R17_PEVA);
  }

  @Test
  void aCycleWithOnlyR17LinkedMissesR21() {
    assertThat(PevaRavaCycleEntity.builder().r17ReportId(1L).build().missingReports())
        .containsExactly(R21_RAVA);
  }

  @Test
  void aCycleWithBothReportsLinkedMissesNothing() {
    assertThat(
            PevaRavaCycleEntity.builder().r17ReportId(1L).r21ReportId(2L).build().missingReports())
        .isEmpty();
  }
}
