package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.investment.TrackingCheckType.BENCHMARK_MODEL;

import ee.tuleva.onboarding.investment.TrackingCheckType;

enum BreachSeverity {
  NOTICE("⚠️", "⚠️ TD NOTICE — HOLDINGS VS BENCHMARKS"),
  BREACH("🛑", "🛑 TD BREACH DETECTED");

  private static final TrackingCheckType CHECK_THE_SISEKORD_SETS_NO_LIMIT_FOR = BENCHMARK_MODEL;

  private final String icon;
  private final String headline;

  BreachSeverity(String icon, String headline) {
    this.icon = icon;
    this.headline = headline;
  }

  static BreachSeverity of(TrackingCheckType checkType) {
    return checkType == CHECK_THE_SISEKORD_SETS_NO_LIMIT_FOR ? NOTICE : BREACH;
  }

  String icon() {
    return icon;
  }

  String headline() {
    return headline;
  }
}
