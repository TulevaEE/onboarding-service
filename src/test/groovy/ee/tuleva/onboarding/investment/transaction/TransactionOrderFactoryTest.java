package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.transaction.TradePrepPricingFixture.noResolvedPrices;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.comparisons.fundvalue.PositionPriceResolver;
import ee.tuleva.onboarding.comparisons.fundvalue.ResolvedPrice;
import ee.tuleva.onboarding.investment.transaction.TradePrepPrice.IndexPrice;
import ee.tuleva.onboarding.investment.transaction.TradePrepPrice.ManualPrice;
import ee.tuleva.onboarding.investment.transaction.TradePrepPricing.FreshestPerInstrument;
import ee.tuleva.onboarding.investment.transaction.TradePrepPricing.OnCommonDate;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionOrderFactoryTest {

  private static final LocalDate AS_OF_DATE = LocalDate.of(2026, 1, 15);
  private static final Instant CREATED_AT = Instant.parse("2026-01-15T10:00:00Z");

  @Mock private PositionPriceResolver positionPriceResolver;

  private TransactionOrderFactory factory;

  @BeforeEach
  void setUp() {
    factory = new TransactionOrderFactory(positionPriceResolver);
  }

  private static TransactionBatch batch() {
    return TransactionBatch.builder().id(1L).fund(TUV100).createdBy("system").build();
  }

  private static FundTransactionInput inputWith(
      Map<String, InstrumentType> instrumentTypes, Map<String, OrderVenue> orderVenues) {
    return FundTransactionInput.builder()
        .fund(TUV100)
        .positions(List.of())
        .modelWeights(List.of())
        .grossPortfolioValue(new BigDecimal("600000"))
        .cashBuffer(new BigDecimal("10000"))
        .liabilities(BigDecimal.ZERO)
        .freeCash(new BigDecimal("90000"))
        .minTransactionThreshold(new BigDecimal("5000"))
        .positionLimits(Map.of())
        .fastSellIsins(Set.of())
        .instrumentTypes(instrumentTypes)
        .orderVenues(orderVenues)
        .build();
  }

  private static FundCalculationResult resultWith(
      FundTransactionInput input, List<TradeCalculation> trades) {
    return new FundCalculationResult(
        TUV100, TransactionMode.BUY, input, trades, BigDecimal.ZERO, null, List.of());
  }

  @Test
  void createOrders_sizesWithThePriceTheGateResolvedWithoutResolvingItAgain() {
    var input = inputWith(Map.of("IE00ETF", InstrumentType.ETF), Map.of("IE00ETF", OrderVenue.FT));
    var trades =
        List.of(
            new TradeCalculation(
                "IE00ETF", new BigDecimal("50000"), new BigDecimal("0.10"), LimitStatus.OK));
    var gatePrice =
        new IndexPrice(
            ResolvedPrice.builder()
                .usedPrice(new BigDecimal("100.00"))
                .priceDate(AS_OF_DATE)
                .build());

    var calculated =
        factory.createOrders(
            batch(),
            resultWith(input, trades),
            AS_OF_DATE,
            CREATED_AT,
            new FreshestPerInstrument(Map.of("IE00ETF", gatePrice)));

    assertThat(calculated.orders().get(0).getOrderQuantity()).isEqualByComparingTo("500.000000");
    assertThat(calculated.priceResolutions()).containsEntry("IE00ETF", gatePrice);
    verifyNoInteractions(positionPriceResolver);
  }

  @Test
  void createOrders_sizesWithAManualPriceAndKeepsItMarkedAsManual() {
    var input = inputWith(Map.of("IE00ETF", InstrumentType.ETF), Map.of("IE00ETF", OrderVenue.FT));
    var trades =
        List.of(
            new TradeCalculation(
                "IE00ETF", new BigDecimal("50000"), new BigDecimal("0.10"), LimitStatus.OK));
    var manual =
        new ManualPrice(new PriceOverride("IE00ETF", new BigDecimal("125"), "Issuer's NAV"));

    var calculated =
        factory.createOrders(
            batch(),
            resultWith(input, trades),
            AS_OF_DATE,
            CREATED_AT,
            new FreshestPerInstrument(Map.of("IE00ETF", manual)));

    var order = calculated.orders().get(0);
    assertThat(order.getOrderQuantity()).isEqualByComparingTo("400.000000");
    assertThat(order.getComment()).isNull();
    assertThat(calculated.priceResolutions()).containsEntry("IE00ETF", manual);
  }

  @Test
  void createOrders_aSaleSizedAboveTheUnitsHeldIsAStopNotACap() {
    var input =
        holding(
            new PositionSnapshot(
                "IE00ETF", new BigDecimal("100000"), new BigDecimal("1000"), new BigDecimal("100")),
            Map.of("IE00ETF", new SellableUnits(new BigDecimal("1000"), BigDecimal.ZERO)));
    var manual =
        new ManualPrice(new PriceOverride("IE00ETF", new BigDecimal("95"), "Issuer's NAV"));

    var calculated = sellOn(input, "-100000", manual);

    assertThat(calculated.orders().get(0).getOrderQuantity()).isEqualByComparingTo("1052.631579");
    assertThat(calculated.unsellable())
        .containsExactly(
            new InputGateFailure.SaleBeyondSellableUnits(
                "IE00ETF",
                new BigDecimal("1052.631579"),
                new BigDecimal("1000"),
                BigDecimal.ZERO,
                new BigDecimal("1000"),
                new BigDecimal("95")));
  }

  @Test
  void createOrders_unitsAnOutstandingSaleAlreadyCommitsCannotBeSoldAgain() {
    var input =
        holding(
            new PositionSnapshot(
                "IE00ETF", new BigDecimal("45400"), new BigDecimal("1000"), new BigDecimal("100")),
            Map.of("IE00ETF", new SellableUnits(new BigDecimal("1000"), new BigDecimal("600"))));
    var manual =
        new ManualPrice(new PriceOverride("IE00ETF", new BigDecimal("91"), "Issuer's NAV"));

    var calculated = sellOn(input, "-45400", manual);

    assertThat(calculated.unsellable())
        .containsExactly(
            new InputGateFailure.SaleBeyondSellableUnits(
                "IE00ETF",
                new BigDecimal("498.901099"),
                new BigDecimal("1000"),
                new BigDecimal("600"),
                new BigDecimal("400"),
                new BigDecimal("91")));
  }

  @Test
  void createOrders_aPositionTheReportDoesNotHoldHasNoUnitsToSell() {
    var input =
        holding(
            new PositionSnapshot("IE00ETF", new BigDecimal("50000"), new BigDecimal("500"), null),
            Map.of());
    var manual =
        new ManualPrice(new PriceOverride("IE00ETF", new BigDecimal("100"), "Issuer's NAV"));

    var calculated = sellOn(input, "-50000", manual);

    assertThat(calculated.unsellable())
        .containsExactly(
            new InputGateFailure.SaleBeyondSellableUnits(
                "IE00ETF",
                new BigDecimal("500.000000"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("100")));
  }

  @Test
  void createOrders_aPartialSaleWithinTheSellableUnitsPasses() {
    var input =
        holding(
            new PositionSnapshot(
                "IE00ETF", new BigDecimal("100000"), new BigDecimal("1000"), new BigDecimal("100")),
            Map.of("IE00ETF", new SellableUnits(new BigDecimal("1000"), BigDecimal.ZERO)));
    var manual =
        new ManualPrice(new PriceOverride("IE00ETF", new BigDecimal("95"), "Issuer's NAV"));

    var calculated = sellOn(input, "-47500", manual);

    assertThat(calculated.orders().get(0).getOrderQuantity()).isEqualByComparingTo("500");
    assertThat(calculated.unsellable()).isEmpty();
  }

  private CalculatedOrders sellOn(FundTransactionInput input, String amount, TradePrepPrice price) {
    return factory.createOrders(
        batch(),
        resultWith(
            input,
            List.of(
                new TradeCalculation(
                    "IE00ETF", new BigDecimal(amount), BigDecimal.ZERO, LimitStatus.OK))),
        AS_OF_DATE,
        CREATED_AT,
        new FreshestPerInstrument(Map.of("IE00ETF", price)));
  }

  private static FundTransactionInput holding(
      PositionSnapshot position, Map<String, SellableUnits> sellable) {
    return FundTransactionInput.builder()
        .fund(TUV100)
        .positions(List.of(position))
        .sellableUnits(sellable)
        .modelWeights(List.of())
        .grossPortfolioValue(new BigDecimal("600000"))
        .cashBuffer(new BigDecimal("10000"))
        .liabilities(BigDecimal.ZERO)
        .freeCash(new BigDecimal("90000"))
        .minTransactionThreshold(new BigDecimal("5000"))
        .positionLimits(Map.of())
        .fastSellIsins(Set.of())
        .instrumentTypes(Map.of("IE00ETF", InstrumentType.ETF))
        .orderVenues(Map.of("IE00ETF", OrderVenue.FT))
        .build();
  }

  @Test
  void createOrders_aPriceOnTheChosenCommonDateIsNotStaleHoweverOldThatDateIs() {
    var input = inputWith(Map.of("IE00ETF", InstrumentType.ETF), Map.of("IE00ETF", OrderVenue.FT));
    var trades =
        List.of(
            new TradeCalculation(
                "IE00ETF", new BigDecimal("50000"), new BigDecimal("0.10"), LimitStatus.OK));
    var commonDate = AS_OF_DATE.minusDays(5);
    var onCommonDate =
        new IndexPrice(
            ResolvedPrice.builder()
                .usedPrice(new BigDecimal("100.00"))
                .priceDate(commonDate)
                .build());

    var calculated =
        factory.createOrders(
            batch(),
            resultWith(input, trades),
            AS_OF_DATE,
            CREATED_AT,
            new OnCommonDate(Map.of("IE00ETF", onCommonDate), commonDate));

    assertThat(calculated.orders().get(0).getComment()).isNull();
  }

  @Test
  void createOrders_buyWithResolvedPrice_computesQuantityAndUsesConfiguredVenue() {
    var input = inputWith(Map.of("IE00ETF", InstrumentType.ETF), Map.of("IE00ETF", OrderVenue.FT));
    var trades =
        List.of(
            new TradeCalculation(
                "IE00ETF", new BigDecimal("50000"), new BigDecimal("0.10"), LimitStatus.OK));
    var resolvedPrice =
        ResolvedPrice.builder().usedPrice(new BigDecimal("100.00")).priceDate(AS_OF_DATE).build();
    given(positionPriceResolver.resolve("IE00ETF", AS_OF_DATE))
        .willReturn(Optional.of(resolvedPrice));

    var calculated =
        factory.createOrders(
            batch(), resultWith(input, trades), AS_OF_DATE, CREATED_AT, noResolvedPrices());

    assertThat(calculated.orders()).hasSize(1);
    var order = calculated.orders().get(0);
    assertThat(order.getTransactionType()).isEqualTo(TransactionType.BUY);
    assertThat(order.getInstrumentType()).isEqualTo(InstrumentType.ETF);
    assertThat(order.getOrderAmount()).isEqualByComparingTo("50000");
    assertThat(order.getOrderQuantity()).isEqualByComparingTo("500.000000");
    assertThat(order.getOrderVenue()).isEqualTo(OrderVenue.FT);
    assertThat(order.getComment()).isNull();
    assertThat(order.getCreatedAt()).isEqualTo(CREATED_AT);
    assertThat(calculated.priceResolutions())
        .containsEntry("IE00ETF", new IndexPrice(resolvedPrice));
  }

  @Test
  void createOrders_negativeTradeAmount_createsSellOrderWithAbsoluteAmount() {
    var input = inputWith(Map.of("IE00ETF", InstrumentType.ETF), Map.of("IE00ETF", OrderVenue.SEB));
    var trades =
        List.of(
            new TradeCalculation(
                "IE00ETF", new BigDecimal("-30000"), new BigDecimal("0.10"), LimitStatus.OK));
    given(positionPriceResolver.resolve("IE00ETF", AS_OF_DATE)).willReturn(Optional.empty());

    var calculated =
        factory.createOrders(
            batch(), resultWith(input, trades), AS_OF_DATE, CREATED_AT, noResolvedPrices());

    var order = calculated.orders().get(0);
    assertThat(order.getTransactionType()).isEqualTo(TransactionType.SELL);
    assertThat(order.getOrderAmount()).isEqualByComparingTo("30000");
  }

  @Test
  void createOrders_zeroTradeAmount_isSkipped() {
    var input =
        inputWith(
            Map.of("IE00A", InstrumentType.ETF, "IE00B", InstrumentType.ETF),
            Map.of("IE00A", OrderVenue.SEB, "IE00B", OrderVenue.SEB));
    var trades =
        List.of(
            new TradeCalculation("IE00A", BigDecimal.ZERO, new BigDecimal("0.10"), LimitStatus.OK),
            new TradeCalculation(
                "IE00B", new BigDecimal("1000"), new BigDecimal("0.10"), LimitStatus.OK));
    given(positionPriceResolver.resolve("IE00B", AS_OF_DATE)).willReturn(Optional.empty());

    var calculated =
        factory.createOrders(
            batch(), resultWith(input, trades), AS_OF_DATE, CREATED_AT, noResolvedPrices());

    assertThat(calculated.orders())
        .extracting(TransactionOrder::getInstrumentIsin)
        .containsExactly("IE00B");
  }

  @Test
  void createOrders_fundBuy_isAmountBased_leavesQuantityNullAndOmitsPriceResolution() {
    var input =
        inputWith(Map.of("IE00FUND", InstrumentType.FUND), Map.of("IE00FUND", OrderVenue.SEB));
    var trades =
        List.of(
            new TradeCalculation(
                "IE00FUND", new BigDecimal("50000"), new BigDecimal("0.10"), LimitStatus.OK));

    var calculated =
        factory.createOrders(
            batch(), resultWith(input, trades), AS_OF_DATE, CREATED_AT, noResolvedPrices());

    var order = calculated.orders().get(0);
    assertThat(order.getOrderQuantity()).isNull();
    assertThat(order.getComment()).isNull();
    assertThat(calculated.priceResolutions()).doesNotContainKey("IE00FUND");
    verifyNoInteractions(positionPriceResolver);
  }

  @Test
  void createOrders_noPriceFound_leavesQuantityNullAndRecordsNullResolution() {
    var input = inputWith(Map.of("IE00ETF", InstrumentType.ETF), Map.of("IE00ETF", OrderVenue.SEB));
    var trades =
        List.of(
            new TradeCalculation(
                "IE00ETF", new BigDecimal("50000"), new BigDecimal("0.10"), LimitStatus.OK));
    given(positionPriceResolver.resolve("IE00ETF", AS_OF_DATE)).willReturn(Optional.empty());

    var calculated =
        factory.createOrders(
            batch(), resultWith(input, trades), AS_OF_DATE, CREATED_AT, noResolvedPrices());

    var order = calculated.orders().get(0);
    assertThat(order.getOrderQuantity()).isNull();
    assertThat(calculated.priceResolutions()).containsEntry("IE00ETF", null);
  }

  @Test
  void createOrders_resolvedPriceWithoutUsedPrice_leavesQuantityNullButRecordsResolution() {
    var input = inputWith(Map.of("IE00ETF", InstrumentType.ETF), Map.of("IE00ETF", OrderVenue.SEB));
    var trades =
        List.of(
            new TradeCalculation(
                "IE00ETF", new BigDecimal("50000"), new BigDecimal("0.10"), LimitStatus.OK));
    var resolvedPrice = ResolvedPrice.builder().usedPrice(null).priceDate(AS_OF_DATE).build();
    given(positionPriceResolver.resolve("IE00ETF", AS_OF_DATE))
        .willReturn(Optional.of(resolvedPrice));

    var calculated =
        factory.createOrders(
            batch(), resultWith(input, trades), AS_OF_DATE, CREATED_AT, noResolvedPrices());

    var order = calculated.orders().get(0);
    assertThat(order.getOrderQuantity()).isNull();
    assertThat(calculated.priceResolutions())
        .containsEntry("IE00ETF", new IndexPrice(resolvedPrice));
  }

  @Test
  void createOrders_nonPositivePrice_leavesQuantityNull() {
    var input = inputWith(Map.of("IE00ETF", InstrumentType.ETF), Map.of("IE00ETF", OrderVenue.SEB));
    var trades =
        List.of(
            new TradeCalculation(
                "IE00ETF", new BigDecimal("50000"), new BigDecimal("0.10"), LimitStatus.OK));
    var resolvedPrice =
        ResolvedPrice.builder().usedPrice(BigDecimal.ZERO).priceDate(AS_OF_DATE).build();
    given(positionPriceResolver.resolve("IE00ETF", AS_OF_DATE))
        .willReturn(Optional.of(resolvedPrice));

    var calculated =
        factory.createOrders(
            batch(), resultWith(input, trades), AS_OF_DATE, CREATED_AT, noResolvedPrices());

    assertThat(calculated.orders().get(0).getOrderQuantity()).isNull();
  }

  @Test
  void createOrders_priceOlderThanStalenessThreshold_setsStaleComment() {
    var input = inputWith(Map.of("IE00ETF", InstrumentType.ETF), Map.of("IE00ETF", OrderVenue.SEB));
    var trades =
        List.of(
            new TradeCalculation(
                "IE00ETF", new BigDecimal("50000"), new BigDecimal("0.10"), LimitStatus.OK));
    var stalePriceDate = AS_OF_DATE.minusDays(4);
    var resolvedPrice =
        ResolvedPrice.builder()
            .usedPrice(new BigDecimal("100.00"))
            .priceDate(stalePriceDate)
            .build();
    given(positionPriceResolver.resolve("IE00ETF", AS_OF_DATE))
        .willReturn(Optional.of(resolvedPrice));

    var calculated =
        factory.createOrders(
            batch(), resultWith(input, trades), AS_OF_DATE, CREATED_AT, noResolvedPrices());

    assertThat(calculated.orders().get(0).getComment())
        .isEqualTo(
            "Sized on stale price: priceDate=%s, ageDays=%d, source=%s"
                .formatted(stalePriceDate, 4, (Object) null));
  }

  @Test
  void createOrders_priceWithinStalenessThreshold_leavesCommentNull() {
    var input = inputWith(Map.of("IE00ETF", InstrumentType.ETF), Map.of("IE00ETF", OrderVenue.SEB));
    var trades =
        List.of(
            new TradeCalculation(
                "IE00ETF", new BigDecimal("50000"), new BigDecimal("0.10"), LimitStatus.OK));
    var resolvedPrice =
        ResolvedPrice.builder()
            .usedPrice(new BigDecimal("100.00"))
            .priceDate(AS_OF_DATE.minusDays(3))
            .build();
    given(positionPriceResolver.resolve("IE00ETF", AS_OF_DATE))
        .willReturn(Optional.of(resolvedPrice));

    var calculated =
        factory.createOrders(
            batch(), resultWith(input, trades), AS_OF_DATE, CREATED_AT, noResolvedPrices());

    assertThat(calculated.orders().get(0).getComment()).isNull();
  }

  @Test
  void requireQuantitiesForNonAmountOrders_throwsWhenNonAmountOrderMissingQuantity() {
    var batch = batch();
    var order =
        TransactionOrder.builder()
            .batch(batch)
            .fund(TUV100)
            .instrumentIsin("IE00ETF")
            .transactionType(TransactionType.BUY)
            .instrumentType(InstrumentType.ETF)
            .orderAmount(new BigDecimal("50000"))
            .orderVenue(OrderVenue.SEB)
            .build();

    assertThatThrownBy(() -> factory.requireQuantitiesForNonAmountOrders(batch, List.of(order)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("IE00ETF");
  }

  @Test
  void requireQuantitiesForNonAmountOrders_allowsAmountBasedOrderWithoutQuantity() {
    var batch = batch();
    var order =
        TransactionOrder.builder()
            .batch(batch)
            .fund(TUV100)
            .instrumentIsin("IE00FUND")
            .transactionType(TransactionType.BUY)
            .instrumentType(InstrumentType.FUND)
            .orderAmount(new BigDecimal("50000"))
            .orderVenue(OrderVenue.SEB)
            .build();

    factory.requireQuantitiesForNonAmountOrders(batch, List.of(order));
  }

  @Test
  void requireQuantitiesForNonAmountOrders_allowsNonAmountOrderWithQuantity() {
    var batch = batch();
    var order =
        TransactionOrder.builder()
            .batch(batch)
            .fund(TUV100)
            .instrumentIsin("IE00ETF")
            .transactionType(TransactionType.BUY)
            .instrumentType(InstrumentType.ETF)
            .orderAmount(new BigDecimal("50000"))
            .orderQuantity(new BigDecimal("500"))
            .orderVenue(OrderVenue.SEB)
            .build();

    factory.requireQuantitiesForNonAmountOrders(batch, List.of(order));
  }
}
