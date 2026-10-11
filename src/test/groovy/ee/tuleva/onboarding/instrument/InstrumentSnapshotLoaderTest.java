package ee.tuleva.onboarding.instrument;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

class InstrumentSnapshotLoaderTest {

  private final InstrumentReferenceRepository instrumentReferenceRepository =
      mock(InstrumentReferenceRepository.class);
  private final BenchmarkCategoryProxyRepository benchmarkCategoryProxyRepository =
      mock(BenchmarkCategoryProxyRepository.class);

  private final InstrumentSnapshotLoader loader =
      new InstrumentSnapshotLoader(instrumentReferenceRepository, benchmarkCategoryProxyRepository);

  @Test
  void aCollidingLookupKeyResolvesToTheFirstRowAndIsReported() {
    var first = instrument("IE00FIRST01", "DUP.DE", "DUP1.XETRA", true);
    var second = instrument("IE00SECOND1", "DUP.PA", "DUP2.XETRA", true);
    given(instrumentReferenceRepository.findAllByOrderByIdAsc()).willReturn(List.of(first, second));
    given(benchmarkCategoryProxyRepository.findAll()).willReturn(List.of());

    var snapshot = loader.loadSnapshot();

    assertThat(snapshot.byShortTicker().get("DUP").getIsin()).isEqualTo("IE00FIRST01");
    assertThat(snapshot.byIsin()).containsKeys("IE00FIRST01", "IE00SECOND1");
    assertThat(snapshot.findings())
        .containsExactly(
            new InstrumentDataFinding.AmbiguousLookupKey(
                "shortTicker", "DUP", List.of("IE00FIRST01", "IE00SECOND1")));
  }

  @Test
  void anInstrumentListedOnEodhdWithoutATickerIsCachedKeptOutOfEodhdFetchingAndReported() {
    var ok = instrument("IE00OK00001", "OK.DE", "OK.XETRA", true);
    var contradictory = instrument("IE00NOTICK1", "NOTICK.DE", null, true);
    given(instrumentReferenceRepository.findAllByOrderByIdAsc())
        .willReturn(List.of(ok, contradictory));
    given(benchmarkCategoryProxyRepository.findAll()).willReturn(List.of());

    var snapshot = loader.loadSnapshot();

    assertThat(snapshot.byIsin()).containsKey("IE00NOTICK1");
    assertThat(snapshot.byEodhdTicker()).containsOnlyKeys("OK.XETRA");
    assertThat(snapshot.findings())
        .containsExactly(new InstrumentDataFinding.EodhdListedWithoutTicker("IE00NOTICK1"));
  }

  @Test
  void aBenchmarkProxyThatHasBeenDeactivatedIsReportedAsAFrozenBenchmark() {
    var retired = deactivated(instrument("IE00RETIRED1", "OLD.DE", "OLD.XETRA", true));
    var current = instrument("IE00CURRENT1", "NEW.DE", "NEW.XETRA", true);
    given(instrumentReferenceRepository.findAllByOrderByIdAsc())
        .willReturn(List.of(retired, current));
    given(benchmarkCategoryProxyRepository.findAll())
        .willReturn(
            List.of(
                new BenchmarkCategoryProxy(1L, "BOND_GLOBAL", "IE00RETIRED1", "IE00RETIRED1", null),
                new BenchmarkCategoryProxy(2L, "BOND_EURO", "IE00CURRENT1", "IE00CURRENT1", null)));

    var snapshot = loader.loadSnapshot();

    assertThat(snapshot.findings())
        .containsExactly(
            new InstrumentDataFinding.InactiveBenchmarkProxy(
                "BOND_GLOBAL", "etfProxyIsin", "IE00RETIRED1"),
            new InstrumentDataFinding.InactiveBenchmarkProxy(
                "BOND_GLOBAL", "indexProxyIsin", "IE00RETIRED1"));
  }

  @Test
  void aProxyIsinWithNoInstrumentRowIsLeftToResolveBenchmarkProxy() {
    var only = instrument("IE00PRESENT1", "OK.DE", "OK.XETRA", true);
    given(instrumentReferenceRepository.findAllByOrderByIdAsc()).willReturn(List.of(only));
    given(benchmarkCategoryProxyRepository.findAll())
        .willReturn(
            List.of(
                new BenchmarkCategoryProxy(
                    1L, "BOND_GLOBAL", "IE00NOTCACHED", "IE00NOTCACHED", null)));

    var snapshot = loader.loadSnapshot();

    assertThat(snapshot.findings()).isEmpty();
  }

  private static InstrumentReference instrument(
      String isin, String yahooTicker, String eodhdTicker, boolean eodhdListed) {
    var instrument = BeanUtils.instantiateClass(InstrumentReference.class);
    ReflectionTestUtils.setField(instrument, "isin", isin);
    ReflectionTestUtils.setField(instrument, "yahooTicker", yahooTicker);
    ReflectionTestUtils.setField(instrument, "eodhdTicker", eodhdTicker);
    ReflectionTestUtils.setField(instrument, "eodhdListed", eodhdListed);
    ReflectionTestUtils.setField(instrument, "active", true);
    return instrument;
  }

  private static InstrumentReference deactivated(InstrumentReference instrument) {
    ReflectionTestUtils.setField(instrument, "active", false);
    return instrument;
  }
}
