package ee.tuleva.onboarding.investment.instrument;

public record BenchmarkCategoryProxy(
    Long id, String benchmarkCategory, String etfProxyStorageKey, String indexProxyKey) {}
