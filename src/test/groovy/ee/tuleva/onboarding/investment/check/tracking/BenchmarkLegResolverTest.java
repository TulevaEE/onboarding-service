package ee.tuleva.onboarding.investment.check.tracking;

import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.FundTicker;
import org.junit.jupiter.api.Test;

class BenchmarkLegResolverTest {

  @Test
  void aMutualFundHoldingIsComparedAgainstTheIndexItself() {
    // iShares Developed World Screened Index Fund -- a .EUFUND holding, so the daily check has the
    // real MSCI World series to compare against and the measured difference is complete.
    var leg = BenchmarkLegResolver.resolve("IE00BFG1TM61").orElseThrow();

    assertThat(leg.seriesKey()).isEqualTo("MSCI_WORLD");
    assertThat(leg.isIndex()).isTrue();
    assertThat(leg.proxyEtf()).isNull();
  }

  @Test
  void anEtfHoldingIsComparedAgainstAProxyEtfWhichIsNetOfItsOwnOcf() {
    // Xtrackers MSCI World Screened, a .XETRA holding. There is no index series for it, so the
    // check uses EUNL -- and EUNL's own OCF is inside that leg.
    var leg = BenchmarkLegResolver.resolve("IE000I9HGDZ3").orElseThrow();

    assertThat(leg.isIndex()).isFalse();
    assertThat(leg.proxyEtf()).isEqualTo(FundTicker.ISHARES_CORE_MSCI_WORLD);
  }

  @Test
  void aBondHoldingUsesAProxyEtfEvenWhenItIsItselfAMutualFund() {
    // iShares Euro Aggregate Bond Index Fund is a .EUFUND holding, but BOND_EURO has no index
    // series configured at all, so even a mutual fund gets a proxy leg here.
    var leg = BenchmarkLegResolver.resolve("LU0826455353").orElseThrow();

    assertThat(leg.isIndex()).isFalse();
    assertThat(leg.proxyEtf()).isEqualTo(FundTicker.ISHARES_EURO_AGG_BOND_ETF);
  }

  @Test
  void anInstrumentWithNoBenchmarkCategoryHasNoLeg() {
    // EUNL is a benchmark, not a tracked holding: it carries no category, so it resolves to
    // nothing rather than to itself.
    assertThat(BenchmarkLegResolver.resolve("IE00B4L5Y983")).isEmpty();
    assertThat(BenchmarkLegResolver.resolve("IE00NOTATRACKER")).isEmpty();
  }
}
