package ee.tuleva.onboarding.investment.risk;

import ee.tuleva.onboarding.investment.series.SeriesSegment;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "investment.risk")
public record RiskIndicatorProperties(Map<TulevaFund, List<SeriesSegment>> sources) {

  public RiskIndicatorProperties {
    sources = sources == null ? Map.of() : sources;
    sources.forEach(RiskIndicatorProperties::refuseSegmentsThatCouldCoverTheSameDays);
  }

  public List<SeriesSegment> sourcesFor(TulevaFund fund) {
    var configured = sources.get(fund);
    if (configured == null || configured.isEmpty()) {
      throw new IllegalStateException("No risk indicator source configured: fund=" + fund);
    }
    return configured;
  }

  private static void refuseSegmentsThatCouldCoverTheSameDays(
      TulevaFund fund, List<SeriesSegment> configured) {
    for (int i = 1; i < configured.size(); i++) {
      var from = configured.get(i).from();
      if (from == null) {
        throw new IllegalStateException(
            "Risk indicator source segment needs a from date: fund=%s, key=%s, position=%d"
                .formatted(fund, configured.get(i).key(), i));
      }
      var previousFrom = configured.get(i - 1).from();
      if (previousFrom != null && !from.isAfter(previousFrom)) {
        throw new IllegalStateException(
            "Risk indicator source segments overlap: fund=%s, key=%s, from=%s, previousFrom=%s"
                .formatted(fund, configured.get(i).key(), from, previousFrom));
      }
    }
  }
}
