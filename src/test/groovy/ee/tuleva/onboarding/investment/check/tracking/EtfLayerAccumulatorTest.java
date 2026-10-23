package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.instrument.InstrumentReferenceFixture.anInstrument;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static java.math.BigDecimal.ONE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import ee.tuleva.onboarding.instrument.BenchmarkProxy;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocation;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EtfLayerAccumulatorTest {

  private static final String EURO_AGGREGATE_FUND = "LU0826455353";
  private static final String EUN4 = "IE00B3DKXQ41";
  private static final String SYBA = "IE00B41RYL63";
  private static final LocalDate SEPTEMBER_END = LocalDate.parse("2026-09-30");

  private final BenchmarkLegResolver benchmarkLegResolver = mock(BenchmarkLegResolver.class);

  @Test
  void theProxyCostAddedBackIsThatOfTheProxyInEffectAtThePeriodEnd() {
    given(benchmarkLegResolver.resolve(EURO_AGGREGATE_FUND, SEPTEMBER_END))
        .willReturn(Optional.of(proxyEtf(EUN4)));
    var accumulator =
        new EtfLayerAccumulator(
            benchmarkLegResolver,
            TUK00,
            SEPTEMBER_END,
            Set.of(EURO_AGGREGATE_FUND),
            Map.of(
                EURO_AGGREGATE_FUND, new BigDecimal("0.0010"),
                EUN4, new BigDecimal("0.0016"),
                SYBA, new BigDecimal("0.0017")));

    accumulator.accumulate(
        ModelPortfolioAllocation.builder()
            .effectiveDate(SEPTEMBER_END)
            .fund(TUK00)
            .isin(EURO_AGGREGATE_FUND)
            .weight(ONE)
            .build());

    assertThat(accumulator.toEtfLayer(new BigDecimal("0.001"), 21, ONE).proxyOcfDrag())
        .isEqualByComparingTo("-0.0016");
  }

  private static BenchmarkProxy proxyEtf(String isin) {
    return new BenchmarkProxy(
        anInstrument().isin(isin).displayName(isin).eodhdTicker("EUN4.XETRA").build(), null);
  }
}
