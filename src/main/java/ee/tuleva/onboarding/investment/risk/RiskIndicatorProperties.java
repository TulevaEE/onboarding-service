package ee.tuleva.onboarding.investment.risk;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "investment.risk")
record RiskIndicatorProperties(
    Map<TulevaFund, List<Source>> sources, Map<TulevaFund, ProxyReview> proxyReview) {

  RiskIndicatorProperties {
    sources = sources == null ? Map.of() : sources;
    proxyReview = proxyReview == null ? Map.of() : proxyReview;
  }

  List<Source> sourcesFor(TulevaFund fund) {
    var configured = sources.get(fund);
    if (configured == null || configured.isEmpty()) {
      throw new IllegalStateException("No risk indicator source configured: fund=" + fund);
    }
    validateSegmentOrder(fund, configured);
    return configured;
  }

  /**
   * Segments carve the timeline up by start date, so every segment after the first has to say where
   * it begins and they have to be in order. A missing or out-of-order date would let two segments
   * cover the same days: the prices interleave, the same-instrument guard then drops nearly every
   * return, and the indicator comes out plausible but wrong instead of failing.
   */
  private void validateSegmentOrder(TulevaFund fund, List<Source> configured) {
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

  @Nullable ProxyReview proxyReviewFor(TulevaFund fund) {
    return proxyReview.get(fund);
  }

  record Source(String key, @Nullable LocalDate from) {}

  record ProxyReview(String ownHistoryKey, int requiredYears) {}
}
