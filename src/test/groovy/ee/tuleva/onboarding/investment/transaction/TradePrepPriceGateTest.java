package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.comparisons.fundvalue.ValidationStatus.NO_PRICE_DATA;
import static ee.tuleva.onboarding.comparisons.fundvalue.ValidationStatus.OK;
import static ee.tuleva.onboarding.investment.transaction.TradePrepOverridesFixture.noOverrides;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;

import ee.tuleva.onboarding.comparisons.fundvalue.PositionPriceResolver;
import ee.tuleva.onboarding.comparisons.fundvalue.ResolvedPrice;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocation;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocationRepository;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import ee.tuleva.onboarding.investment.transaction.InputGateFailure.ManualPriceDeviates;
import ee.tuleva.onboarding.investment.transaction.InputGateFailure.MissingPrices;
import ee.tuleva.onboarding.investment.transaction.InputGateFailure.NoCommonPriceDate;
import ee.tuleva.onboarding.investment.transaction.TradePrepPrice.IndexPrice;
import ee.tuleva.onboarding.investment.transaction.TradePrepPrice.ManualPrice;
import ee.tuleva.onboarding.investment.transaction.TradePrepPricing.FreshestPerInstrument;
import ee.tuleva.onboarding.investment.transaction.TradePrepPricing.OnCommonDate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TradePrepPriceGateTest {

  private static final LocalDate TRADE_DATE = LocalDate.of(2026, 11, 18);
  private static final LocalDate POSITION_DATE = LocalDate.of(2026, 11, 17);

  @Mock private FundPositionRepository fundPositionRepository;
  @Mock private PositionAssembler positionAssembler;
  @Mock private ModelPortfolioAllocationRepository modelPortfolioAllocationRepository;
  @Mock private PositionPriceResolver positionPriceResolver;
  @Mock private ManualPriceDeviations manualPriceDeviations;

  @InjectMocks private TradePrepPriceGate gates;

  @BeforeEach
  void noManualPriceDeviatesUnlessATestSaysSo() {
    lenient()
        .when(manualPriceDeviations.check(any(), any(), any()))
        .thenReturn(new PriceDeviations(List.of(), List.of(), List.of()));
  }

  @Test
  void listsTheHeldAndModelInstrumentsTradePrepHasNoPriceFor() {
    givenHeld("HELD_PRICED", "HELD_UNKNOWN_INSTRUMENT", "IN_BOTH");
    givenModel(model("MODEL_NO_PRICE_DATA", "0.4"), model("IN_BOTH", "0.6"));
    givenPrice("HELD_PRICED", "100.00");
    given(positionPriceResolver.resolve("HELD_UNKNOWN_INSTRUMENT", TRADE_DATE))
        .willReturn(Optional.empty());
    given(positionPriceResolver.resolve("MODEL_NO_PRICE_DATA", TRADE_DATE))
        .willReturn(Optional.of(ResolvedPrice.builder().validationStatus(NO_PRICE_DATA).build()));
    givenPrice("IN_BOTH", "25.00");

    assertThat(gates.check(TKF100, TRADE_DATE, noOverrides()).failures())
        .contains(new MissingPrices(List.of("HELD_UNKNOWN_INSTRUMENT", "MODEL_NO_PRICE_DATA")));
  }

  @Test
  void aPriceOfZeroOrBelowIsNoPrice() {
    givenHeld("ZERO_PRICE");
    givenModel();
    givenPrice("ZERO_PRICE", "0");

    assertThat(gates.check(TKF100, TRADE_DATE, noOverrides()).failures())
        .contains(new MissingPrices(List.of("ZERO_PRICE")));
  }

  @Test
  void aModelInstrumentWithoutWeightNeedsNoPrice() {
    given(fundPositionRepository.findLatestNavDateByFundAndAsOfDate(TKF100, TRADE_DATE))
        .willReturn(Optional.empty());
    givenModel(model("SOLD_OUT_OF_THE_MODEL", "0"));

    assertThat(gates.check(TKF100, TRADE_DATE, noOverrides()).failures()).isEmpty();
  }

  @Test
  void anExcludedInstrumentNeedsNoPrice() {
    givenHeld("EXCLUDED");
    givenModel(model("EXCLUDED", "1"));

    assertThat(
            gates
                .check(
                    TKF100,
                    TRADE_DATE,
                    new TradePrepOverrides(
                        Set.of("EXCLUDED"),
                        List.of(),
                        null,
                        PriceDateRule.FRESHEST_PER_INSTRUMENT,
                        List.of()))
                .failures())
        .isEmpty();
  }

  @Test
  void aHoldingTheSebReportListsAtZeroNeedsNoPrice() {
    given(fundPositionRepository.findLatestNavDateByFundAndAsOfDate(TKF100, TRADE_DATE))
        .willReturn(Optional.of(POSITION_DATE));
    given(positionAssembler.assemble(TKF100, POSITION_DATE, PendingOrderImpact.none()))
        .willReturn(
            List.of(new PositionSnapshot("SOLD_OUT", ZERO, new BigDecimal("0.000000"), null)));
    givenModel();

    assertThat(gates.check(TKF100, TRADE_DATE, noOverrides()).failures()).isEmpty();
  }

  @Test
  void aManualPriceStandsInForAMissingOneAndSaysItClearedTheStop() {
    givenHeld("HELD_NO_PRICE");
    givenModel();
    given(positionPriceResolver.resolve("HELD_NO_PRICE", TRADE_DATE)).willReturn(Optional.empty());
    var manual = new PriceOverride("HELD_NO_PRICE", new BigDecimal("12.5"), "Issuer's NAV");

    var check =
        gates.check(
            TKF100,
            TRADE_DATE,
            new TradePrepOverrides(
                Set.of(), List.of(manual), null, PriceDateRule.FRESHEST_PER_INSTRUMENT, List.of()));

    assertThat(check)
        .isEqualTo(
            new PriceCheck(
                List.of(),
                new FreshestPerInstrument(Map.of("HELD_NO_PRICE", new ManualPrice(manual))),
                List.of(new UsedPriceOverride(manual, GateNeed.CLEARED_A_STOP, null)),
                List.of(),
                List.of()));
  }

  @Test
  void aManualPriceForAPricedInstrumentIsStillUsedAndSaysTheGateDidNotNeedIt() {
    givenHeld("HELD_PRICED");
    givenModel();
    givenPrice("HELD_PRICED", "100.00");
    var manual = new PriceOverride("HELD_PRICED", new BigDecimal("98"), "Index price is stale");

    var check =
        gates.check(
            TKF100,
            TRADE_DATE,
            new TradePrepOverrides(
                Set.of(), List.of(manual), null, PriceDateRule.FRESHEST_PER_INSTRUMENT, List.of()));

    assertThat(check.pricing())
        .isEqualTo(new FreshestPerInstrument(Map.of("HELD_PRICED", new ManualPrice(manual))));
    assertThat(check.priceOverrides())
        .singleElement()
        .satisfies(
            used -> {
              assertThat(used.need()).isEqualTo(GateNeed.NOT_NEEDED);
              assertThat(used.indexPrice().usedPrice()).isEqualByComparingTo("100.00");
            });
  }

  @Test
  void aManualPriceForAnInstrumentTheFundNeitherHoldsNorHasInItsModelIsRejected() {
    givenHeld("HELD_PRICED");
    givenModel();
    var stranger = new PriceOverride("NOT_IN_THE_FUND", new BigDecimal("1"), "Typo");

    assertThatThrownBy(
            () ->
                gates.check(
                    TKF100,
                    TRADE_DATE,
                    new TradePrepOverrides(
                        Set.of(),
                        List.of(stranger),
                        null,
                        PriceDateRule.FRESHEST_PER_INSTRUMENT,
                        List.of())))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void twoManualPricesForOneInstrumentAreRejected() {
    givenHeld("HELD_PRICED");
    givenModel();
    var first = new PriceOverride("HELD_PRICED", new BigDecimal("1"), "First");
    var second = new PriceOverride("HELD_PRICED", new BigDecimal("2"), "Second");

    assertThatThrownBy(
            () ->
                gates.check(
                    TKF100,
                    TRADE_DATE,
                    new TradePrepOverrides(
                        Set.of(),
                        List.of(first, second),
                        null,
                        PriceDateRule.FRESHEST_PER_INSTRUMENT,
                        List.of())))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void theCommonDateRuleStepsBackToTheLatestDayEveryInstrumentHasAPriceOn() {
    givenHeld("A", "B");
    givenModel();
    LocalDate dayBefore = TRADE_DATE.minusDays(1);
    givenPriceOn("A", TRADE_DATE, TRADE_DATE, "10");
    givenPriceOn("B", TRADE_DATE, dayBefore, "20");
    givenPriceOn("A", dayBefore, dayBefore, "9");
    givenPriceOn("B", dayBefore, dayBefore, "20");

    var check = gates.check(TKF100, TRADE_DATE, commonDate());

    assertThat(check)
        .isEqualTo(
            new PriceCheck(
                List.of(),
                new OnCommonDate(
                    Map.of(
                        "A", new IndexPrice(priceOn(dayBefore, "9")),
                        "B", new IndexPrice(priceOn(dayBefore, "20"))),
                    dayBefore),
                List.of(),
                List.of(),
                List.of()));
  }

  @Test
  void theCommonDateRuleStopsTheFundWhenNoDayInTheLookbackHasEveryPrice() {
    givenHeld("A", "B");
    givenModel();
    LocalDate dayBefore = TRADE_DATE.minusDays(1);
    givenPriceOn("A", TRADE_DATE, TRADE_DATE, "10");
    givenPriceOn("B", TRADE_DATE, dayBefore, "20");
    given(positionPriceResolver.resolve("A", dayBefore)).willReturn(Optional.empty());
    given(positionPriceResolver.resolve("B", dayBefore))
        .willReturn(Optional.of(priceOn(dayBefore, "20")));

    assertThat(gates.check(TKF100, TRADE_DATE, commonDate()).failures())
        .contains(new NoCommonPriceDate(TRADE_DATE.minusDays(14), TRADE_DATE));
  }

  @Test
  void aManualPriceIsLeftOutOfTheCommonDateSearch() {
    givenHeld("A", "B");
    givenModel();
    givenPriceOn("A", TRADE_DATE, TRADE_DATE, "10");
    givenPriceOn("B", TRADE_DATE, TRADE_DATE.minusDays(3), "20");
    var manual = new PriceOverride("B", new BigDecimal("21"), "Issuer's NAV");

    var check =
        gates.check(
            TKF100,
            TRADE_DATE,
            new TradePrepOverrides(
                Set.of(), List.of(manual), null, PriceDateRule.LATEST_COMMON_DATE, List.of()));

    assertThat(check.pricing())
        .isEqualTo(
            new OnCommonDate(
                Map.of(
                    "A", new IndexPrice(priceOn(TRADE_DATE, "10")), "B", new ManualPrice(manual)),
                TRADE_DATE));
  }

  @Test
  void underTheCommonDateRuleAManualPriceIsMeasuredAgainstTheCommonDate() {
    givenHeld("A", "B", "C");
    givenModel();
    LocalDate dayBefore = TRADE_DATE.minusDays(1);
    givenPriceOn("A", TRADE_DATE, TRADE_DATE, "10");
    givenPriceOn("A", dayBefore, TRADE_DATE.minusDays(3), "9");
    givenPriceOn("B", TRADE_DATE, dayBefore, "20");
    givenPriceOn("C", TRADE_DATE, TRADE_DATE, "30");
    givenPriceOn("C", dayBefore, dayBefore, "29");
    var manualA = new PriceOverride("A", new BigDecimal("11"), "No price on the common date");
    var manualC = new PriceOverride("C", new BigDecimal("31"), "Price on the common date is off");

    var check =
        gates.check(
            TKF100,
            TRADE_DATE,
            new TradePrepOverrides(
                Set.of(),
                List.of(manualA, manualC),
                null,
                PriceDateRule.LATEST_COMMON_DATE,
                List.of()));

    assertThat(check.priceOverrides())
        .containsExactly(
            new UsedPriceOverride(manualA, GateNeed.CLEARED_A_STOP, null),
            new UsedPriceOverride(manualC, GateNeed.NOT_NEEDED, priceOn(dayBefore, "29")));
  }

  @Test
  void underTheCommonDateRuleAFundPricedOnlyByHandHasNoCommonDate() {
    givenHeld("A");
    givenModel();
    givenPriceOn("A", TRADE_DATE, TRADE_DATE, "10");
    var manual = new PriceOverride("A", new BigDecimal("11"), "Issuer's NAV");

    var check =
        gates.check(
            TKF100,
            TRADE_DATE,
            new TradePrepOverrides(
                Set.of(), List.of(manual), null, PriceDateRule.LATEST_COMMON_DATE, List.of()));

    assertThat(check.pricing())
        .isEqualTo(new FreshestPerInstrument(Map.of("A", new ManualPrice(manual))));
  }

  private static TradePrepOverrides commonDate() {
    return new TradePrepOverrides(
        Set.of(), List.of(), null, PriceDateRule.LATEST_COMMON_DATE, List.of());
  }

  private void givenPriceOn(String isin, LocalDate asked, LocalDate priced, String price) {
    given(positionPriceResolver.resolve(isin, asked))
        .willReturn(Optional.of(priceOn(priced, price)));
  }

  private static ResolvedPrice priceOn(LocalDate date, String price) {
    return ResolvedPrice.builder()
        .usedPrice(new BigDecimal(price))
        .validationStatus(OK)
        .priceDate(date)
        .build();
  }

  @Test
  void theGateHandsTheIndexPricesItResolvedToTheRunSoTheyAreNotResolvedAgain() {
    givenHeld("HELD_PRICED");
    givenModel();
    givenPrice("HELD_PRICED", "100.00");

    assertThat(gates.check(TKF100, TRADE_DATE, noOverrides()).pricing())
        .isEqualTo(
            new FreshestPerInstrument(
                Map.of("HELD_PRICED", new IndexPrice(priceOn(POSITION_DATE, "100.00")))));
  }

  @Test
  void anUnconfirmedDeviationStopsTheFundAndAConfirmedOneIsKeptForTheAudit() {
    givenHeld("A");
    givenModel();
    givenPrice("A", "100.00");
    var unconfirmed =
        new PriceDeviation("A", new BigDecimal("50"), new BigDecimal("100.00"), POSITION_DATE);
    var confirmed =
        new PriceDeviation("A", new BigDecimal("80"), new BigDecimal("100.00"), POSITION_DATE);
    given(manualPriceDeviations.check(any(), any(), any()))
        .willReturn(new PriceDeviations(List.of(unconfirmed), List.of(confirmed), List.of()));

    var check = gates.check(TKF100, TRADE_DATE, noOverrides());

    assertThat(check.failures()).containsExactly(new ManualPriceDeviates(List.of(unconfirmed)));
    assertThat(check.confirmedDeviations()).containsExactly(confirmed);
  }

  @Test
  void everyInstrumentPricedPasses() {
    givenHeld("HELD_PRICED");
    givenModel(model("HELD_PRICED", "1"));
    givenPrice("HELD_PRICED", "100.00");

    assertThat(gates.check(TKF100, TRADE_DATE, noOverrides()).failures()).isEmpty();
  }

  private void givenHeld(String... isins) {
    given(fundPositionRepository.findLatestNavDateByFundAndAsOfDate(TKF100, TRADE_DATE))
        .willReturn(Optional.of(POSITION_DATE));
    given(positionAssembler.assemble(TKF100, POSITION_DATE, PendingOrderImpact.none()))
        .willReturn(
            List.of(isins).stream()
                .map(isin -> new PositionSnapshot(isin, new BigDecimal("1000")))
                .toList());
  }

  private void givenModel(ModelPortfolioAllocation... allocations) {
    given(modelPortfolioAllocationRepository.findLatestByFundAsOf(TKF100, TRADE_DATE))
        .willReturn(List.of(allocations));
  }

  private void givenPrice(String isin, String price) {
    given(positionPriceResolver.resolve(isin, TRADE_DATE))
        .willReturn(
            Optional.of(
                ResolvedPrice.builder()
                    .usedPrice(new BigDecimal(price))
                    .validationStatus(OK)
                    .priceDate(POSITION_DATE)
                    .build()));
  }

  private static ModelPortfolioAllocation model(String isin, String weight) {
    return ModelPortfolioAllocation.builder()
        .fund(TKF100)
        .isin(isin)
        .weight(new BigDecimal(weight))
        .effectiveDate(LocalDate.of(2026, 1, 1))
        .build();
  }
}
