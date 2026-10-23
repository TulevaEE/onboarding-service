package ee.tuleva.onboarding.investment.check.tracking;

import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.instrument.BenchmarkProxy;
import ee.tuleva.onboarding.instrument.InstrumentReferenceService;
import ee.tuleva.onboarding.time.ClockConfig;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

@DataJpaTest
@ComponentScan(basePackageClasses = InstrumentReferenceService.class)
@Import({ClockConfig.class, BenchmarkLegResolver.class})
class BenchmarkLegResolverTest {

  private static final LocalDate LAST_DAY_BEFORE_U09_SWAP = LocalDate.parse("2026-10-21");
  private static final LocalDate U09_SWAP_DAY = LocalDate.parse("2026-10-22");

  @Autowired private BenchmarkLegResolver resolver;

  @Test
  void aMutualFundEquityHoldingIsComparedAgainstTheIndexSeriesItself() {
    var leg = resolver.resolve("IE00BFG1TM61", U09_SWAP_DAY).orElseThrow();

    assertThat(leg.storageKey()).isEqualTo("MSCI_WORLD");
    assertThat(leg.isIndex()).isTrue();
    assertThat(leg.proxyInstrument()).isNull();
  }

  @Test
  void anEtfHoldingIsComparedAgainstAProxyEtfWhichIsNetOfItsOwnOcf() {
    var leg = resolver.resolve("IE000I9HGDZ3", U09_SWAP_DAY).orElseThrow();

    assertThat(leg.isIndex()).isFalse();
    assertThat(leg.proxyInstrument().getIsin()).isEqualTo("IE00B4L5Y983");
    assertThat(leg.storageKey()).isEqualTo("IE00B4L5Y983.XETR");
  }

  @Test
  void aEuronextParisListedHoldingIsAnEtfHoldingToo() {
    var leg = resolver.resolve("IE000F60HVH9", U09_SWAP_DAY).orElseThrow();

    assertThat(leg.isIndex()).isFalse();
    assertThat(leg.proxyInstrument().getIsin()).isEqualTo("IE00B4L5Y983");
    assertThat(leg.storageKey()).isEqualTo("IE00B4L5Y983.XETR");
  }

  @Test
  void aBondHoldingUsesAProxyEtfEvenWhenItIsItselfAMutualFund() {
    var leg = resolver.resolve("LU0826455353", U09_SWAP_DAY).orElseThrow();

    assertThat(leg.isIndex()).isFalse();
    assertThat(leg.proxyInstrument().getIsin()).isEqualTo("IE00B3DKXQ41");
    assertThat(leg.storageKey()).isEqualTo("IE00B3DKXQ41.XETR");
  }

  @Test
  void aRecomputedDateIsMeasuredAgainstTheProxyInEffectOnThatDate() {
    assertThat(resolver.resolve("LU0839970364", LAST_DAY_BEFORE_U09_SWAP))
        .map(BenchmarkProxy::storageKey)
        .contains("LU1708330318.XPAR");
    assertThat(resolver.resolve("LU0839970364", U09_SWAP_DAY))
        .map(BenchmarkProxy::storageKey)
        .contains("IE00BDBRDM35.XETR");
  }

  @Test
  void aBenchmarkProxyEtfIsNotItselfATrackedHoldingSoItHasNoLeg() {
    assertThat(resolver.resolve("IE00B4L5Y983", U09_SWAP_DAY)).isEmpty();
    assertThat(resolver.resolve("IE00NOTATRACKER", U09_SWAP_DAY)).isEmpty();
  }
}
