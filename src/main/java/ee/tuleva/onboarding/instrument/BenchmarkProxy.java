package ee.tuleva.onboarding.instrument;

import static java.util.Objects.requireNonNull;

import org.jspecify.annotations.Nullable;

public record BenchmarkProxy(
    @Nullable InstrumentReference proxyInstrument, @Nullable String indexSeriesKey) {

  public BenchmarkProxy {
    if (proxyInstrument == null && indexSeriesKey == null) {
      throw new IllegalArgumentException(
          "Benchmark proxy needs either a proxy instrument or an index series key");
    }
  }

  public boolean isIndex() {
    return proxyInstrument == null;
  }

  public String storageKey() {
    var instrument = proxyInstrument;
    if (instrument == null) {
      return requireNonNull(indexSeriesKey);
    }
    return instrument
        .getExchangeStorageKey()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Benchmark proxy instrument has no exchange storage key: isin="
                        + instrument.getIsin()));
  }
}
