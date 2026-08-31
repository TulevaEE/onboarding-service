package ee.tuleva.onboarding.instrument;

import static ee.tuleva.onboarding.instrument.InstrumentReferenceFixture.instrument;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class InstrumentDataFindingTest {

  @Test
  void ambiguousLookupKeyReportsBothIsins() {
    var finding =
        new InstrumentDataFinding.AmbiguousLookupKey(
            "yahoo ticker", "IWDA", List.of("IE00B4L5Y983", "IE00BFG1TM61"));

    assertThat(finding.isins()).containsExactly("IE00B4L5Y983", "IE00BFG1TM61");
    assertThat(finding.describe())
        .isEqualTo(
            "Ambiguous yahoo ticker, resolving to the first row: key=IWDA, isins=[IE00B4L5Y983, IE00BFG1TM61]");
  }

  @Test
  void eodhdListedWithoutTickerReportsTheIsin() {
    var finding = new InstrumentDataFinding.EodhdListedWithoutTicker("IE00BFG1TM61");

    assertThat(finding.isins()).containsExactly("IE00BFG1TM61");
    assertThat(finding.describe())
        .isEqualTo(
            "Listed on EODHD but has no EODHD ticker, excluded from EODHD price fetching: isin=IE00BFG1TM61");
  }

  @Test
  void benchmarkProxyWithoutStorageKeyFailsFast() {
    var proxyWithoutKey = new BenchmarkProxy(instrument("IE00BFG1TM61").build(), null);

    assertThatThrownBy(proxyWithoutKey::storageKey).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void indexBenchmarkProxyUsesTheSeriesKey() {
    var indexProxy = new BenchmarkProxy(null, "MSCI_WORLD_NET");

    assertThat(indexProxy.isIndex()).isTrue();
    assertThat(indexProxy.storageKey()).isEqualTo("MSCI_WORLD_NET");
  }
}
