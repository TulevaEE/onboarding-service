package ee.tuleva.onboarding.investment.risk;

import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ee.tuleva.onboarding.investment.series.SeriesSegment;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RiskIndicatorPropertiesTest {

  @Test
  void aSingleSourceNeedsNoStartDate() {
    var properties = propertiesWith(List.of(new SeriesSegment("MSCI_ACWI", null)));

    assertThat(properties.sourcesFor(TKF100)).hasSize(1);
  }

  @Test
  void aFundWithNoConfiguredSourceIsARefusalRatherThanAnEmptySeries() {
    var properties = new RiskIndicatorProperties(null);

    assertThatThrownBy(() -> properties.sourcesFor(TKF100))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void aFundConfiguredWithAnEmptySourceListIsARefusalToo() {
    var properties = propertiesWith(List.of());

    assertThatThrownBy(() -> properties.sourcesFor(TKF100))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void aSplicedSourceThatDoesNotSayWhereALaterSegmentStartsFailsWhenTheConfigIsBound() {
    var segments =
        List.of(new SeriesSegment("MSCI_ACWI", null), new SeriesSegment("EE0000003283", null));

    assertThatThrownBy(() -> propertiesWith(segments)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void splicedSegmentsOutOfOrderFailWhenTheConfigIsBound() {
    var segments =
        List.of(
            new SeriesSegment("MSCI_ACWI", LocalDate.of(2026, 2, 2)),
            new SeriesSegment("EE0000003283", LocalDate.of(2020, 1, 1)));

    assertThatThrownBy(() -> propertiesWith(segments)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void aWellFormedSpliceIsAccepted() {
    var properties =
        propertiesWith(
            List.of(
                new SeriesSegment("MSCI_ACWI", null),
                new SeriesSegment("EE0000003283", LocalDate.of(2026, 2, 2))));

    assertThat(properties.sourcesFor(TKF100)).hasSize(2);
  }

  private static RiskIndicatorProperties propertiesWith(List<SeriesSegment> sources) {
    return new RiskIndicatorProperties(Map.of(TKF100, sources));
  }
}
