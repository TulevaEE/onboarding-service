package ee.tuleva.onboarding.investment.risk;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ee.tuleva.onboarding.investment.risk.RiskIndicatorProperties.Source;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RiskIndicatorPropertiesTest {

  @Test
  void aSingleSourceNeedsNoStartDate() {
    var properties = propertiesWith(List.of(new Source("MSCI_ACWI", null)));

    assertThat(properties.sourcesFor(TKF100)).hasSize(1);
  }

  @Test
  void aSplicedSourceMustSayWhereEachLaterSegmentStarts() {
    var properties =
        propertiesWith(List.of(new Source("MSCI_ACWI", null), new Source("EE0000003283", null)));

    assertThatThrownBy(() -> properties.sourcesFor(TKF100))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void splicedSegmentsMustStartInOrder() {
    var properties =
        propertiesWith(
            List.of(
                new Source("MSCI_ACWI", LocalDate.of(2026, 2, 2)),
                new Source("EE0000003283", LocalDate.of(2020, 1, 1))));

    assertThatThrownBy(() -> properties.sourcesFor(TKF100))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void aWellFormedSpliceIsAccepted() {
    var properties =
        propertiesWith(
            List.of(
                new Source("MSCI_ACWI", null),
                new Source("EE0000003283", LocalDate.of(2026, 2, 2))));

    assertThat(properties.sourcesFor(TKF100)).hasSize(2);
  }

  private static RiskIndicatorProperties propertiesWith(List<Source> sources) {
    return new RiskIndicatorProperties(Map.of(TKF100, sources), Map.of());
  }
}
