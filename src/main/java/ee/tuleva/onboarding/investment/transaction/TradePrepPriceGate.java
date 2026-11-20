package ee.tuleva.onboarding.investment.transaction;

import ee.tuleva.onboarding.comparisons.fundvalue.PositionPriceResolver;
import ee.tuleva.onboarding.comparisons.fundvalue.ResolvedPrice;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocation;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocationRepository;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import ee.tuleva.onboarding.investment.transaction.InputGateFailure.MissingPrices;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class TradePrepPriceGate {

  private final FundPositionRepository fundPositionRepository;
  private final PositionAssembler positionAssembler;
  private final ModelPortfolioAllocationRepository modelPortfolioAllocationRepository;
  private final PositionPriceResolver positionPriceResolver;

  PriceCheck check(TulevaFund fund, LocalDate asOfDate, TradePrepOverrides overrides) {
    List<String> instruments =
        Stream.concat(heldIsins(fund, asOfDate), modelIsins(fund, asOfDate))
            .distinct()
            .sorted()
            .toList();
    Map<String, BigDecimal> manualPrices = manualPrices(fund, instruments, overrides.prices());
    Map<String, ResolvedPrice> indexPrices = new HashMap<>();
    instruments.stream()
        .filter(isin -> !overrides.excludedIsins().contains(isin))
        .forEach(
            isin -> indexPrice(isin, asOfDate).ifPresent(price -> indexPrices.put(isin, price)));
    List<String> unpriced =
        instruments.stream()
            .filter(isin -> !overrides.excludedIsins().contains(isin))
            .filter(isin -> !indexPrices.containsKey(isin) && !manualPrices.containsKey(isin))
            .toList();
    return new PriceCheck(
        unpriced.isEmpty() ? Optional.empty() : Optional.of(new MissingPrices(unpriced)),
        new TradePrepPricing(manualPrices),
        overrides.prices().stream()
            .map(override -> used(override, overrides.excludedIsins(), indexPrices))
            .toList());
  }

  private static UsedPriceOverride used(
      PriceOverride override, Set<String> excludedIsins, Map<String, ResolvedPrice> indexPrices) {
    ResolvedPrice indexPrice = indexPrices.get(override.isin());
    boolean clearedAStop = indexPrice == null && !excludedIsins.contains(override.isin());
    return new UsedPriceOverride(
        override, clearedAStop ? GateNeed.CLEARED_A_STOP : GateNeed.NOT_NEEDED, indexPrice);
  }

  private static Map<String, BigDecimal> manualPrices(
      TulevaFund fund, List<String> instruments, List<PriceOverride> overrides) {
    List<String> unknown =
        overrides.stream()
            .map(PriceOverride::isin)
            .filter(isin -> !instruments.contains(isin))
            .sorted()
            .toList();
    if (!unknown.isEmpty()) {
      throw new IllegalArgumentException(
          "Manual price for an instrument neither held nor in the model portfolio: fund=%s, isins=%s"
              .formatted(fund.name(), unknown));
    }
    Map<String, BigDecimal> prices = new HashMap<>();
    overrides.forEach(
        override -> {
          if (prices.put(override.isin(), override.price()) != null) {
            throw new IllegalArgumentException(
                "Two manual prices for one instrument: fund=%s, isin=%s"
                    .formatted(fund.name(), override.isin()));
          }
        });
    return Map.copyOf(prices);
  }

  private Stream<String> heldIsins(TulevaFund fund, LocalDate asOfDate) {
    return fundPositionRepository.findLatestNavDateByFundAndAsOfDate(fund, asOfDate).stream()
        .flatMap(
            positionDate ->
                positionAssembler.assemble(fund, positionDate, PendingOrderImpact.none()).stream())
        .filter(TradePrepPriceGate::isNotSoldOut)
        .map(PositionSnapshot::isin)
        .filter(Objects::nonNull);
  }

  private Stream<String> modelIsins(TulevaFund fund, LocalDate asOfDate) {
    return modelPortfolioAllocationRepository.findLatestByFundAsOf(fund, asOfDate).stream()
        .filter(allocation -> allocation.getWeight().signum() > 0)
        .map(ModelPortfolioAllocation::getIsin)
        .filter(Objects::nonNull);
  }

  private static boolean isNotSoldOut(PositionSnapshot position) {
    BigDecimal quantity = position.quantity();
    return quantity == null || quantity.signum() != 0;
  }

  private Optional<ResolvedPrice> indexPrice(String isin, LocalDate asOfDate) {
    return positionPriceResolver
        .resolve(isin, asOfDate)
        .filter(resolved -> resolved.usedPrice() != null && resolved.usedPrice().signum() > 0);
  }
}
