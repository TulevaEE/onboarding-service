package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.instrument.InstrumentReferenceFixture.anInstrument;
import static ee.tuleva.onboarding.instrument.InstrumentReferenceServiceFixture.instrumentReferenceService;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.TRACKING_BREACH_THRESHOLD;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.TRACKING_MAX_DAILY_RETURN;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.FundValueProvider;
import ee.tuleva.onboarding.comparisons.fundvalue.PriorityPriceProvider;
import ee.tuleva.onboarding.instrument.BenchmarkCategoryProxy;
import ee.tuleva.onboarding.instrument.InstrumentReference;
import ee.tuleva.onboarding.investment.check.tracking.TrackingDifferenceCalculator.PriceSnapshot;
import ee.tuleva.onboarding.investment.check.tracking.TrackingDifferenceCalculator.SecurityData;
import ee.tuleva.onboarding.investment.config.InvestmentParameterRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BenchmarkCheckBuilderTest {

  private static final LocalDate CHECK_DATE = LocalDate.of(2026, 4, 10);
  private static final LocalDate PREVIOUS_DATE = LocalDate.of(2026, 4, 9);
  private static final String EMERGING_MARKETS_ISIN = "IE00BKPTWY98";
  private static final String DEVELOPED_MARKETS_ISIN = "IE00BFG1TM61";
  private static final String UNTRACKED_ISIN = "IE00NOTATRACKER";
  private static final String EMERGING_MARKETS_INDEX = "MSCI_EM";
  private static final String DEVELOPED_MARKETS_INDEX = "MSCI_WORLD";
  private static final String FLAT_PRICE = "20.00";

  @Mock private InvestmentParameterRepository parameterRepository;
  @Mock private FundValueProvider fundValueProvider;
  @Mock private PriorityPriceProvider priorityPriceProvider;
  @Mock private ConsecutiveBreachTracker consecutiveBreachTracker;

  private BenchmarkCheckBuilder builder;

  @BeforeEach
  void setUp() {
    builder =
        new BenchmarkCheckBuilder(
            new TrackingDifferenceCalculator(parameterRepository),
            fundValueProvider,
            priorityPriceProvider,
            new BenchmarkLegResolver(
                instrumentReferenceService(
                    List.of(
                        tracked(EMERGING_MARKETS_ISIN, "IE00BKPTWY98.EUFUND", "EQUITY_EM"),
                        tracked(DEVELOPED_MARKETS_ISIN, "IE00BFG1TM61.EUFUND", "EQUITY_DM"),
                        tracked("IE00B4L5YC18", "EUNM.XETRA", null),
                        tracked("IE00B4L5Y983", "EUNL.XETRA", null)),
                    List.of(
                        new BenchmarkCategoryProxy(
                            1L, "EQUITY_EM", "IE00B4L5YC18", null, EMERGING_MARKETS_INDEX),
                        new BenchmarkCategoryProxy(
                            2L, "EQUITY_DM", "IE00B4L5Y983", null, DEVELOPED_MARKETS_INDEX)))),
            consecutiveBreachTracker);
    given(parameterRepository.findLatestValue(TRACKING_MAX_DAILY_RETURN, CHECK_DATE))
        .willReturn(new BigDecimal("0.5"));
    given(parameterRepository.findLatestValue(TRACKING_BREACH_THRESHOLD, CHECK_DATE))
        .willReturn(new BigDecimal("0.001"));
    given(fundValueProvider.getLatestValue(EMERGING_MARKETS_INDEX, CHECK_DATE))
        .willReturn(Optional.of(indexValue(CHECK_DATE)));
    given(fundValueProvider.getLatestValue(EMERGING_MARKETS_INDEX, PREVIOUS_DATE))
        .willReturn(Optional.of(indexValue(PREVIOUS_DATE)));
  }

  @Test
  void aHoldingWithNoBenchmarkProxyIsReportedAsABenchmarkGapRatherThanDropped() {
    var result =
        builder
            .buildBenchmarkModelCheck(
                TUK75,
                CHECK_DATE,
                List.of(
                    flatHolding(EMERGING_MARKETS_ISIN, new BigDecimal("0.60")),
                    flatHolding(UNTRACKED_ISIN, new BigDecimal("0.40"))))
            .orElseThrow();

    assertThat(result.benchmarkGapIsins()).containsExactly(UNTRACKED_ISIN);
    assertThat(result.benchmarkGapWeight()).isEqualByComparingTo(new BigDecimal("0.40"));
  }

  @Test
  void aHoldingWhoseBenchmarkHasNoPricesIsReportedAsABenchmarkGapRatherThanDropped() {
    given(fundValueProvider.getLatestValue(DEVELOPED_MARKETS_INDEX, CHECK_DATE))
        .willReturn(Optional.empty());
    given(fundValueProvider.getLatestValue(DEVELOPED_MARKETS_INDEX, PREVIOUS_DATE))
        .willReturn(Optional.empty());

    var result =
        builder
            .buildBenchmarkModelCheck(
                TUK75,
                CHECK_DATE,
                List.of(
                    flatHolding(EMERGING_MARKETS_ISIN, new BigDecimal("0.50")),
                    flatHolding(DEVELOPED_MARKETS_ISIN, new BigDecimal("0.30")),
                    flatHolding(UNTRACKED_ISIN, new BigDecimal("0.20"))))
            .orElseThrow();

    assertThat(result.benchmarkGapIsins()).containsExactly(DEVELOPED_MARKETS_ISIN, UNTRACKED_ISIN);
    assertThat(result.benchmarkGapWeight()).isEqualByComparingTo(new BigDecimal("0.50"));
  }

  @Test
  void aFullyCoveredSleeveReportsNoBenchmarkGap() {
    var result =
        builder
            .buildBenchmarkModelCheck(
                TUK75, CHECK_DATE, List.of(flatHolding(EMERGING_MARKETS_ISIN, BigDecimal.ONE)))
            .orElseThrow();

    assertThat(result.benchmarkGapIsins()).isEmpty();
    assertThat(result.benchmarkGapWeight()).isEqualByComparingTo(BigDecimal.ZERO);
  }

  private static SecurityData flatHolding(String isin, BigDecimal actualWeight) {
    return new SecurityData(
        isin,
        actualWeight,
        actualWeight,
        new PriceSnapshot(new BigDecimal(FLAT_PRICE), CHECK_DATE),
        new PriceSnapshot(new BigDecimal(FLAT_PRICE), PREVIOUS_DATE));
  }

  private static FundValue indexValue(LocalDate date) {
    return new FundValue(
        EMERGING_MARKETS_INDEX, date, new BigDecimal("500.00"), "TEST", Instant.EPOCH);
  }

  private static InstrumentReference tracked(
      String isin, String eodhdTicker, String benchmarkCategory) {
    return anInstrument()
        .isin(isin)
        .displayName(isin)
        .eodhdTicker(eodhdTicker)
        .benchmarkCategory(benchmarkCategory)
        .active(true)
        .build();
  }
}
