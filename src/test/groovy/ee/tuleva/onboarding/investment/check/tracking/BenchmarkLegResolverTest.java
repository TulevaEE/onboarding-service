package ee.tuleva.onboarding.investment.check.tracking;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class BenchmarkLegResolverTest {

  @Autowired private BenchmarkLegResolver resolver;

  @Test
  void aMutualFundEquityHoldingIsComparedAgainstTheIndexSeriesItself() {
    var leg = resolver.resolve("IE00BFG1TM61").orElseThrow();

    assertThat(leg.storageKey()).isEqualTo("MSCI_WORLD");
    assertThat(leg.isIndex()).isTrue();
    assertThat(leg.proxyInstrument()).isNull();
  }

  @Test
  void anEtfHoldingIsComparedAgainstAProxyEtfWhichIsNetOfItsOwnOcf() {
    var leg = resolver.resolve("IE000I9HGDZ3").orElseThrow();

    assertThat(leg.isIndex()).isFalse();
    assertThat(leg.proxyInstrument().getIsin()).isEqualTo("IE00B4L5Y983");
    assertThat(leg.storageKey()).isEqualTo("IE00B4L5Y983.XETR");
  }

  @Test
  void aEuronextParisListedHoldingIsAnEtfHoldingToo() {
    var leg = resolver.resolve("IE000F60HVH9").orElseThrow();

    assertThat(leg.isIndex()).isFalse();
    assertThat(leg.proxyInstrument().getIsin()).isEqualTo("IE00B4L5Y983");
    assertThat(leg.storageKey()).isEqualTo("IE00B4L5Y983.XETR");
  }

  @Test
  void aBondHoldingUsesAProxyEtfEvenWhenItIsItselfAMutualFund() {
    var leg = resolver.resolve("LU0826455353").orElseThrow();

    assertThat(leg.isIndex()).isFalse();
    assertThat(leg.proxyInstrument().getIsin()).isEqualTo("IE00B3DKXQ41");
    assertThat(leg.storageKey()).isEqualTo("IE00B3DKXQ41.XETR");
  }

  @Test
  void aBenchmarkProxyEtfIsNotItselfATrackedHoldingSoItHasNoLeg() {
    assertThat(resolver.resolve("IE00B4L5Y983")).isEmpty();
    assertThat(resolver.resolve("IE00NOTATRACKER")).isEmpty();
  }
}
