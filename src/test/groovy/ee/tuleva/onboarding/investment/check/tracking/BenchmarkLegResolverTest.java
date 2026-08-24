package ee.tuleva.onboarding.investment.check.tracking;

import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.FundTicker;
import org.junit.jupiter.api.Test;

class BenchmarkLegResolverTest {

  private final BenchmarkLegResolver resolver = new BenchmarkLegResolver();

  @Test
  void aMutualFundEquityHoldingIsComparedAgainstTheIndexSeriesItself() {
    var leg = resolver.resolve("IE00BFG1TM61").orElseThrow();

    assertThat(leg.seriesKey()).isEqualTo("MSCI_WORLD");
    assertThat(leg.isIndex()).isTrue();
    assertThat(leg.proxyEtf()).isNull();
  }

  @Test
  void anEtfHoldingIsComparedAgainstAProxyEtfWhichIsNetOfItsOwnOcf() {
    var leg = resolver.resolve("IE000I9HGDZ3").orElseThrow();

    assertThat(leg.isIndex()).isFalse();
    assertThat(leg.proxyEtf()).isEqualTo(FundTicker.ISHARES_CORE_MSCI_WORLD);
  }

  @Test
  void aBondHoldingUsesAProxyEtfEvenWhenItIsItselfAMutualFund() {
    var leg = resolver.resolve("LU0826455353").orElseThrow();

    assertThat(leg.isIndex()).isFalse();
    assertThat(leg.proxyEtf()).isEqualTo(FundTicker.ISHARES_EURO_AGG_BOND_ETF);
  }

  @Test
  void aBenchmarkProxyEtfIsNotItselfATrackedHoldingSoItHasNoLeg() {
    assertThat(resolver.resolve("IE00B4L5Y983")).isEmpty();
    assertThat(resolver.resolve("IE00NOTATRACKER")).isEmpty();
  }
}
