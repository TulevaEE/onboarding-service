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
    return configured;
  }

  @Nullable ProxyReview proxyReviewFor(TulevaFund fund) {
    return proxyReview.get(fund);
  }

  record Source(String key, @Nullable LocalDate from) {}

  record ProxyReview(String ownHistoryKey, int requiredYears) {}
}
