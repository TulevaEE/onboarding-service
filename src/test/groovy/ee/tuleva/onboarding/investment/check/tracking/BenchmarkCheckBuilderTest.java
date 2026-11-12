package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.instrument.InstrumentReferenceFixture.anInstrument;
import static ee.tuleva.onboarding.instrument.InstrumentReferenceServiceFixture.instrumentReferenceService;
import static ee.tuleva.onboarding.investment.TrackingCheckType.BENCHMARK_MODEL;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.TRACKING_BREACH_THRESHOLD;
import static ee.tuleva.onboarding.investment.config.InvestmentParameter.TRACKING_MAX_DAILY_RETURN;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.FundValueProvider;
import ee.tuleva.onboarding.comparisons.fundvalue.PriorityPriceProvider;
import ee.tuleva.onboarding.instrument.BenchmarkCategoryProxy;
import ee.tuleva.onboarding.instrument.InstrumentReference;
import ee.tuleva.onboarding.instrument.InstrumentReferenceService;
import ee.tuleva.onboarding.instrument.InstrumentReferenceServiceFixture.PastProxy;
import ee.tuleva.onboarding.investment.check.tracking.BenchmarkModelCheck.Measured;
import ee.tuleva.onboarding.investment.check.tracking.BenchmarkModelCheck.NoHoldingMeasured;
import ee.tuleva.onboarding.investment.check.tracking.BenchmarkModelCheck.NothingToMeasure;
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
  private static final String DEVELOPED_MARKETS_ETF_ISIN = "IE000I9HGDZ3";
  private static final String DEVELOPED_MARKETS_PROXY_ISIN = "IE00B4L5Y983";
  private static final String PROXY_DEUTSCHE_BOERSE_KEY = "IE00B4L5Y983.XETR";
  private static final String PROXY_EODHD_KEY = "EUNL.XETRA";

  @Mock private InvestmentParameterRepository parameterRepository;
  @Mock private FundValueProvider fundValueProvider;
  @Mock private ConsecutiveBreachTracker consecutiveBreachTracker;

  private BenchmarkCheckBuilder builder;

  @BeforeEach
  void setUp() {
    builder = builderOver(instruments(List.of()));
    lenient()
        .when(parameterRepository.findLatestValue(TRACKING_MAX_DAILY_RETURN, CHECK_DATE))
        .thenReturn(new BigDecimal("0.5"));
    lenient()
        .when(parameterRepository.findLatestValue(TRACKING_BREACH_THRESHOLD, CHECK_DATE))
        .thenReturn(new BigDecimal("0.001"));
    lenient()
        .when(fundValueProvider.getLatestValue(EMERGING_MARKETS_INDEX, CHECK_DATE))
        .thenReturn(Optional.of(indexValue(CHECK_DATE)));
    lenient()
        .when(fundValueProvider.getLatestValue(EMERGING_MARKETS_INDEX, PREVIOUS_DATE))
        .thenReturn(Optional.of(indexValue(PREVIOUS_DATE)));
  }

  @Test
  void aHoldingIsMeasuredAgainstTheProxyInEffectOnTheCheckDate() {
    givenProxyCloseInBothSources(CHECK_DATE, "22.00");
    givenProxyCloseInBothSources(PREVIOUS_DATE, FLAT_PRICE);
    given(consecutiveBreachTracker.countConsecutiveBreaches(TUK75, BENCHMARK_MODEL, CHECK_DATE))
        .willReturn(streakOf(0, 0, 0));
    var builderWithAPastProxy =
        builderOver(
            instruments(
                List.of(
                    new PastProxy(DEVELOPED_MARKETS_ISIN, "IE00B4L5YC18", PREVIOUS_DATE),
                    new PastProxy(DEVELOPED_MARKETS_ISIN, "IE00B4L5Y983", CHECK_DATE))));

    var result =
        measured(
            builderWithAPastProxy.buildBenchmarkModelCheck(
                TUK75,
                CHECK_DATE,
                List.of(
                    flatHolding(EMERGING_MARKETS_ISIN, new BigDecimal("0.50")),
                    flatHolding(DEVELOPED_MARKETS_ISIN, new BigDecimal("0.50")))));

    assertThat(result.securityAttributions())
        .filteredOn(attribution -> attribution.isin().equals(DEVELOPED_MARKETS_ISIN))
        .singleElement()
        .extracting(SecurityAttribution::benchmarkReturn)
        .satisfies(benchmarkReturn -> assertThat(benchmarkReturn).isEqualByComparingTo("0.1"));
  }

  @Test
  void anEtfHoldingIsMeasuredAgainstItsProxysEodhdCloseWhenDeutscheBoerseHasNotStoredTheDay() {
    given(fundValueProvider.getLatestValue(PROXY_DEUTSCHE_BOERSE_KEY, CHECK_DATE))
        .willReturn(Optional.of(price(PROXY_DEUTSCHE_BOERSE_KEY, PREVIOUS_DATE, "128.65")));
    given(fundValueProvider.getLatestValue(PROXY_DEUTSCHE_BOERSE_KEY, PREVIOUS_DATE))
        .willReturn(Optional.of(price(PROXY_DEUTSCHE_BOERSE_KEY, PREVIOUS_DATE, "128.65")));
    given(fundValueProvider.getLatestValue(PROXY_EODHD_KEY, CHECK_DATE))
        .willReturn(Optional.of(price(PROXY_EODHD_KEY, CHECK_DATE, "130.955")));
    given(fundValueProvider.getLatestValue(PROXY_EODHD_KEY, PREVIOUS_DATE))
        .willReturn(Optional.of(price(PROXY_EODHD_KEY, PREVIOUS_DATE, "128.65")));
    given(consecutiveBreachTracker.countConsecutiveBreaches(TUK75, BENCHMARK_MODEL, CHECK_DATE))
        .willReturn(streakOf(0, 0, 0));

    var result =
        measured(
            builder.buildBenchmarkModelCheck(
                TUK75,
                CHECK_DATE,
                List.of(
                    new SecurityData(
                        DEVELOPED_MARKETS_ETF_ISIN,
                        BigDecimal.ONE,
                        BigDecimal.ONE,
                        new PriceSnapshot(new BigDecimal("10.40"), CHECK_DATE),
                        new PriceSnapshot(new BigDecimal("10.182"), PREVIOUS_DATE)))));

    assertThat(result.securityAttributions())
        .singleElement()
        .satisfies(
            attribution -> {
              assertThat(attribution.benchmarkReturn()).isEqualByComparingTo("0.017917");
              assertThat(attribution.staleBenchmarkPrices()).isEmpty();
            });
  }

  @Test
  void anEtfHoldingWhoseProxyHasNoCloseForTheDayInAnySourceIsMeasuredAgainstTheLastOneAndFlagged() {
    givenProxyCloseInBothSources(PREVIOUS_DATE, "128.65");
    given(fundValueProvider.getLatestValue(PROXY_DEUTSCHE_BOERSE_KEY, CHECK_DATE))
        .willReturn(Optional.of(price(PROXY_DEUTSCHE_BOERSE_KEY, PREVIOUS_DATE, "128.65")));
    given(fundValueProvider.getLatestValue(PROXY_EODHD_KEY, CHECK_DATE))
        .willReturn(Optional.of(price(PROXY_EODHD_KEY, PREVIOUS_DATE, "128.65")));
    given(consecutiveBreachTracker.countConsecutiveBreaches(TUK75, BENCHMARK_MODEL, CHECK_DATE))
        .willReturn(streakOf(0, 0, 0));

    var result =
        measured(
            builder.buildBenchmarkModelCheck(
                TUK75, CHECK_DATE, List.of(risingHolding(DEVELOPED_MARKETS_ETF_ISIN))));

    assertThat(result.securityAttributions())
        .singleElement()
        .satisfies(
            attribution -> {
              assertThat(attribution.benchmarkReturn()).isEqualByComparingTo("0");
              assertThat(attribution.staleBenchmarkPrices())
                  .containsExactly(
                      new StaleBenchmarkPrice(
                          DEVELOPED_MARKETS_PROXY_ISIN, CHECK_DATE, PREVIOUS_DATE));
            });
  }

  @Test
  void aMutualFundWhoseIndexSeriesLacksItsPreviousPriceDateIsFlaggedUnderTheSeriesKey() {
    var twoDaysBefore = PREVIOUS_DATE.minusDays(1);
    given(fundValueProvider.getLatestValue(EMERGING_MARKETS_INDEX, PREVIOUS_DATE))
        .willReturn(
            Optional.of(
                new FundValue(
                    EMERGING_MARKETS_INDEX,
                    twoDaysBefore,
                    new BigDecimal("500.00"),
                    "TEST",
                    Instant.EPOCH)));

    var result =
        measured(
            builder.buildBenchmarkModelCheck(
                TUK75, CHECK_DATE, List.of(flatHolding(EMERGING_MARKETS_ISIN, BigDecimal.ONE))));

    assertThat(result.securityAttributions())
        .singleElement()
        .extracting(SecurityAttribution::staleBenchmarkPrices)
        .isEqualTo(
            List.of(new StaleBenchmarkPrice(EMERGING_MARKETS_INDEX, PREVIOUS_DATE, twoDaysBefore)));
  }

  @Test
  void aHoldingWithoutANewPriceAsksItsIndexForOneDateAndIsFlaggedOnceWhenThatDateIsMissing() {
    given(fundValueProvider.getLatestValue(PROXY_DEUTSCHE_BOERSE_KEY, PREVIOUS_DATE))
        .willReturn(
            Optional.of(price(PROXY_DEUTSCHE_BOERSE_KEY, PREVIOUS_DATE.minusDays(1), "128.00")));
    given(fundValueProvider.getLatestValue(PROXY_EODHD_KEY, PREVIOUS_DATE))
        .willReturn(Optional.of(price(PROXY_EODHD_KEY, PREVIOUS_DATE.minusDays(1), "128.00")));
    var unchangedSincePreviousDate =
        new SecurityData(
            DEVELOPED_MARKETS_ETF_ISIN,
            BigDecimal.ONE,
            BigDecimal.ONE,
            new PriceSnapshot(new BigDecimal(FLAT_PRICE), PREVIOUS_DATE),
            new PriceSnapshot(new BigDecimal(FLAT_PRICE), PREVIOUS_DATE));

    var result =
        measured(
            builder.buildBenchmarkModelCheck(
                TUK75, CHECK_DATE, List.of(unchangedSincePreviousDate)));

    assertThat(result.securityAttributions())
        .singleElement()
        .extracting(SecurityAttribution::staleBenchmarkPrices)
        .isEqualTo(
            List.of(
                new StaleBenchmarkPrice(
                    DEVELOPED_MARKETS_PROXY_ISIN, PREVIOUS_DATE, PREVIOUS_DATE.minusDays(1))));
  }

  private void givenProxyCloseInBothSources(LocalDate date, String close) {
    given(fundValueProvider.getLatestValue(PROXY_DEUTSCHE_BOERSE_KEY, date))
        .willReturn(Optional.of(price(PROXY_DEUTSCHE_BOERSE_KEY, date, close)));
    given(fundValueProvider.getLatestValue(PROXY_EODHD_KEY, date))
        .willReturn(Optional.of(price(PROXY_EODHD_KEY, date, close)));
  }

  private static FundValue price(String key, LocalDate date, String value) {
    return new FundValue(key, date, new BigDecimal(value), "TEST", Instant.EPOCH);
  }

  private BenchmarkCheckBuilder builderOver(InstrumentReferenceService instruments) {
    return new BenchmarkCheckBuilder(
        new TrackingDifferenceCalculator(parameterRepository),
        new BenchmarkPrices(
            fundValueProvider, new PriorityPriceProvider(fundValueProvider, instruments)),
        new BenchmarkLegResolver(instruments),
        consecutiveBreachTracker);
  }

  private static InstrumentReferenceService instruments(List<PastProxy> pastProxies) {
    return instrumentReferenceService(
        List.of(
            tracked(EMERGING_MARKETS_ISIN, "IE00BKPTWY98.EUFUND", "EQUITY_EM"),
            tracked(DEVELOPED_MARKETS_ISIN, "IE00BFG1TM61.EUFUND", "EQUITY_DM"),
            tracked(DEVELOPED_MARKETS_ETF_ISIN, "XWSC.XETRA", "EQUITY_DM"),
            tracked("IE00B4L5YC18", "EUNM.XETRA", null),
            tracked(DEVELOPED_MARKETS_PROXY_ISIN, PROXY_EODHD_KEY, null)),
        List.of(
            new BenchmarkCategoryProxy(
                1L, "EQUITY_EM", "IE00B4L5YC18", null, EMERGING_MARKETS_INDEX),
            new BenchmarkCategoryProxy(
                2L, "EQUITY_DM", DEVELOPED_MARKETS_PROXY_ISIN, null, DEVELOPED_MARKETS_INDEX)),
        pastProxies);
  }

  @Test
  void aHoldingWithNoBenchmarkProxyIsReportedAsABenchmarkGapRatherThanDropped() {
    var result =
        measured(
            builder.buildBenchmarkModelCheck(
                TUK75,
                CHECK_DATE,
                List.of(
                    flatHolding(EMERGING_MARKETS_ISIN, new BigDecimal("0.60")),
                    flatHolding(UNTRACKED_ISIN, new BigDecimal("0.40")))));

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
        measured(
            builder.buildBenchmarkModelCheck(
                TUK75,
                CHECK_DATE,
                List.of(
                    flatHolding(EMERGING_MARKETS_ISIN, new BigDecimal("0.50")),
                    flatHolding(DEVELOPED_MARKETS_ISIN, new BigDecimal("0.30")),
                    flatHolding(UNTRACKED_ISIN, new BigDecimal("0.20")))));

    assertThat(result.benchmarkGapIsins()).containsExactly(DEVELOPED_MARKETS_ISIN, UNTRACKED_ISIN);
    assertThat(result.benchmarkGapWeight()).isEqualByComparingTo(new BigDecimal("0.50"));
  }

  @Test
  void aFullyCoveredSleeveReportsNoBenchmarkGap() {
    var result =
        measured(
            builder.buildBenchmarkModelCheck(
                TUK75, CHECK_DATE, List.of(flatHolding(EMERGING_MARKETS_ISIN, BigDecimal.ONE))));

    assertThat(result.benchmarkGapIsins()).isEmpty();
    assertThat(result.benchmarkGapWeight()).isEqualByComparingTo(BigDecimal.ZERO);
  }

  @Test
  void aBenchmarkModelDayWithinLimitsAfterThreeBreachDaysOwesNoFourthDayNotification() {
    given(consecutiveBreachTracker.countConsecutiveBreaches(TUK75, BENCHMARK_MODEL, CHECK_DATE))
        .willReturn(streakOf(3, 0, 0));

    var result =
        measured(
            builder.buildBenchmarkModelCheck(
                TUK75, CHECK_DATE, List.of(flatHolding(EMERGING_MARKETS_ISIN, BigDecimal.ONE))));

    assertThat(result.breach()).isFalse();
    assertThat(result.consecutiveBreachDays()).isZero();
    assertThat(result.streakBefore()).isNull();
  }

  @Test
  void aBreachingBenchmarkModelDayTakesItsStreakLengthAndUncheckedDaysFromTheTracker() {
    given(consecutiveBreachTracker.countConsecutiveBreaches(TUK75, BENCHMARK_MODEL, CHECK_DATE))
        .willReturn(streakOf(2, 0, 1));

    var result =
        measured(
            builder.buildBenchmarkModelCheck(
                TUK75, CHECK_DATE, List.of(risingHolding(EMERGING_MARKETS_ISIN))));

    assertThat(result.breach()).isTrue();
    assertThat(result.consecutiveBreachDays()).isEqualTo(4);
    assertThat(result.escalationUncheckedDays()).isEqualTo(1);
  }

  private static ConsecutiveBreachTracker.ConsecutiveBreachInfo streakOf(
      int count, int uncheckedDays, int uncheckedDaysSince) {
    return new ConsecutiveBreachTracker.ConsecutiveBreachInfo(
        count,
        new BigDecimal("0.0045"),
        new BigDecimal("0.0045"),
        BigDecimal.ZERO,
        java.util.Map.of(),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        false,
        false,
        false,
        uncheckedDays,
        uncheckedDaysSince);
  }

  private static SecurityData risingHolding(String isin) {
    return new SecurityData(
        isin,
        BigDecimal.ONE,
        BigDecimal.ONE,
        new PriceSnapshot(new BigDecimal("20.20"), CHECK_DATE),
        new PriceSnapshot(new BigDecimal(FLAT_PRICE), PREVIOUS_DATE));
  }

  @Test
  void aSleeveWhoseEveryBenchmarkHadNoPriceIsNamedAsMeasuringNoHolding() {
    var result =
        builder.buildBenchmarkModelCheck(
            TUK75,
            CHECK_DATE,
            List.of(
                flatHolding(DEVELOPED_MARKETS_ISIN, new BigDecimal("0.70")),
                flatHolding(UNTRACKED_ISIN, new BigDecimal("0.30"))));

    assertThat(result)
        .isEqualTo(
            new NoHoldingMeasured(
                TUK75,
                CHECK_DATE,
                List.of(DEVELOPED_MARKETS_ISIN + " (" + DEVELOPED_MARKETS_INDEX + ")")));
  }

  @Test
  void anUnheldAllocationWhoseBenchmarkHadNoPriceIsNotNamedSinceItsPriceWouldStillMeasureNothing() {
    var result =
        builder.buildBenchmarkModelCheck(
            TUK75,
            CHECK_DATE,
            List.of(
                flatHolding(DEVELOPED_MARKETS_ISIN, BigDecimal.ZERO),
                flatHolding(UNTRACKED_ISIN, BigDecimal.ONE)));

    assertThat(result).isEqualTo(new NothingToMeasure());
  }

  @Test
  void aSleeveWithNoBenchmarkProxyAtAllHasNothingToMeasureSinceNoPriceWouldEverMeasureIt() {
    var result =
        builder.buildBenchmarkModelCheck(
            TUK75, CHECK_DATE, List.of(flatHolding(UNTRACKED_ISIN, BigDecimal.ONE)));

    assertThat(result).isEqualTo(new NothingToMeasure());
  }

  @Test
  void aSleeveWithoutAPricedHoldingHasNothingToMeasure() {
    var unpriced =
        new SecurityData(
            EMERGING_MARKETS_ISIN,
            BigDecimal.ONE,
            BigDecimal.ONE,
            new PriceSnapshot(null, CHECK_DATE),
            new PriceSnapshot(null, PREVIOUS_DATE));

    var result = builder.buildBenchmarkModelCheck(TUK75, CHECK_DATE, List.of(unpriced));

    assertThat(result).isEqualTo(new NothingToMeasure());
  }

  private static TrackingDifferenceResult measured(BenchmarkModelCheck check) {
    assertThat(check).isInstanceOf(Measured.class);
    return ((Measured) check).result();
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
