package ee.tuleva.onboarding.investment.kid;

import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ee.tuleva.onboarding.investment.risk.RiskIndicatorProperties;
import ee.tuleva.onboarding.investment.series.SeriesSegment;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class KidConfigurationTest {

  @Test
  void anOwnNavSegmentStartingOnAMonthsFirstDayBoots() {
    var sources = sourcesWithOwnNavFrom(LocalDate.of(2026, 3, 1));

    assertThat(new KidConfiguration().kidFunds(sources)).isEqualTo(KidFunds.TULEVA_FUNDS);
  }

  @Test
  void anOwnNavSegmentStartingInsideAMonthFailsTheBootRatherThanALaterKidCall() {
    var sources = sourcesWithOwnNavFrom(LocalDate.of(2026, 3, 2));

    assertThatThrownBy(() -> new KidConfiguration().kidFunds(sources))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void aKidFundWithNoConfiguredSeriesFailsTheBoot() {
    var sources = new RiskIndicatorProperties(Map.of());

    assertThatThrownBy(() -> new KidConfiguration().kidFunds(sources))
        .isInstanceOf(IllegalStateException.class);
  }

  private static RiskIndicatorProperties sourcesWithOwnNavFrom(LocalDate from) {
    return new RiskIndicatorProperties(
        Map.of(
            TKF100,
            List.of(
                new SeriesSegment("MSCI_ACWI", null), new SeriesSegment(TKF100.getIsin(), from))));
  }
}
