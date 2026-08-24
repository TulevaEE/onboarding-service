package ee.tuleva.onboarding.instrument;

import org.jspecify.annotations.Nullable;

public record BenchmarkCategoryProxy(
    Long id,
    String benchmarkCategory,
    String etfProxyIsin,
    @Nullable String indexProxyIsin,
    @Nullable String indexSeriesKey) {}
