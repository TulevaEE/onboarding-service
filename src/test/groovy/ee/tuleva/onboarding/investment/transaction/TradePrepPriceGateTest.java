package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.comparisons.fundvalue.ValidationStatus.NO_PRICE_DATA;
import static ee.tuleva.onboarding.comparisons.fundvalue.ValidationStatus.OK;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.comparisons.fundvalue.PositionPriceResolver;
import ee.tuleva.onboarding.comparisons.fundvalue.ResolvedPrice;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocation;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocationRepository;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import ee.tuleva.onboarding.investment.transaction.InputGateFailure.MissingPrices;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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

  @InjectMocks private TradePrepPriceGate gates;

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

    assertThat(gates.missingPrices(TKF100, TRADE_DATE, Set.of()))
        .contains(new MissingPrices(List.of("HELD_UNKNOWN_INSTRUMENT", "MODEL_NO_PRICE_DATA")));
  }

  @Test
  void aPriceOfZeroOrBelowIsNoPrice() {
    givenHeld("ZERO_PRICE");
    givenModel();
    givenPrice("ZERO_PRICE", "0");

    assertThat(gates.missingPrices(TKF100, TRADE_DATE, Set.of()))
        .contains(new MissingPrices(List.of("ZERO_PRICE")));
  }

  @Test
  void aModelInstrumentWithoutWeightNeedsNoPrice() {
    given(fundPositionRepository.findLatestNavDateByFundAndAsOfDate(TKF100, TRADE_DATE))
        .willReturn(Optional.empty());
    givenModel(model("SOLD_OUT_OF_THE_MODEL", "0"));

    assertThat(gates.missingPrices(TKF100, TRADE_DATE, Set.of())).isEmpty();
  }

  @Test
  void anExcludedInstrumentNeedsNoPrice() {
    givenHeld("EXCLUDED");
    givenModel(model("EXCLUDED", "1"));

    assertThat(gates.missingPrices(TKF100, TRADE_DATE, Set.of("EXCLUDED"))).isEmpty();
  }

  @Test
  void aHoldingTheSebReportListsAtZeroNeedsNoPrice() {
    given(fundPositionRepository.findLatestNavDateByFundAndAsOfDate(TKF100, TRADE_DATE))
        .willReturn(Optional.of(POSITION_DATE));
    given(positionAssembler.assemble(TKF100, POSITION_DATE, PendingOrderImpact.none()))
        .willReturn(
            List.of(new PositionSnapshot("SOLD_OUT", ZERO, new BigDecimal("0.000000"), null)));
    givenModel();

    assertThat(gates.missingPrices(TKF100, TRADE_DATE, Set.of())).isEmpty();
  }

  @Test
  void everyInstrumentPricedPasses() {
    givenHeld("HELD_PRICED");
    givenModel(model("HELD_PRICED", "1"));
    givenPrice("HELD_PRICED", "100.00");

    assertThat(gates.missingPrices(TKF100, TRADE_DATE, Set.of())).isEmpty();
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
