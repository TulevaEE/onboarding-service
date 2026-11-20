package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.comparisons.fundvalue.PriorityPriceProvider.MAX_LOOKBACK_DAYS;
import static java.util.Comparator.naturalOrder;
import static java.util.stream.Collectors.toMap;

import ee.tuleva.onboarding.comparisons.fundvalue.PositionPriceResolver;
import ee.tuleva.onboarding.comparisons.fundvalue.ResolvedPrice;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocation;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocationRepository;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import ee.tuleva.onboarding.investment.transaction.InputGateFailure.MissingPrices;
import ee.tuleva.onboarding.investment.transaction.InputGateFailure.NoCommonPriceDate;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
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
    ManualPrices manual = ManualPrices.of(fund, instruments, overrides.prices());
    Map<String, ResolvedPrice> indexPrices =
        instruments.stream()
            .filter(isin -> !overrides.excludedIsins().contains(isin))
            .flatMap(
                isin -> indexPrice(isin, asOfDate).map(price -> Map.entry(isin, price)).stream())
            .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
    List<String> unpriced =
        instruments.stream()
            .filter(isin -> !overrides.excludedIsins().contains(isin))
            .filter(isin -> !indexPrices.containsKey(isin) && !manual.prices(isin))
            .toList();
    if (!unpriced.isEmpty()) {
      return checked(new MissingPrices(unpriced), manual, overrides, indexPrices);
    }
    if (overrides.priceDateRule() == PriceDateRule.FRESHEST_PER_INSTRUMENT) {
      return passed(null, manual, overrides, indexPrices);
    }
    return latestCommonDate(
            indexPrices.keySet().stream().filter(isin -> !manual.prices(isin)).toList(), asOfDate)
        .map(date -> passed(date, manual, overrides, indexPrices))
        .orElseGet(
            () ->
                checked(
                    new NoCommonPriceDate(asOfDate.minusDays(MAX_LOOKBACK_DAYS), asOfDate),
                    manual,
                    overrides,
                    indexPrices));
  }

  private static PriceCheck checked(
      InputGateFailure failure,
      ManualPrices manual,
      TradePrepOverrides overrides,
      Map<String, ResolvedPrice> indexPrices) {
    return new PriceCheck(
        Optional.of(failure),
        manual.pricing(null),
        manual.uses(overrides.excludedIsins(), indexPrices));
  }

  private static PriceCheck passed(
      @Nullable LocalDate priceDate,
      ManualPrices manual,
      TradePrepOverrides overrides,
      Map<String, ResolvedPrice> indexPrices) {
    return new PriceCheck(
        Optional.empty(),
        manual.pricing(priceDate),
        manual.uses(overrides.excludedIsins(), indexPrices));
  }

  private Optional<LocalDate> latestCommonDate(List<String> isins, LocalDate asOfDate) {
    LocalDate earliestAllowed = asOfDate.minusDays(MAX_LOOKBACK_DAYS);
    LocalDate candidate = asOfDate;
    while (!candidate.isBefore(earliestAllowed)) {
      LocalDate day = candidate;
      List<Optional<LocalDate>> priceDates =
          isins.stream().map(isin -> indexPrice(isin, day).map(ResolvedPrice::priceDate)).toList();
      if (priceDates.stream().anyMatch(Optional::isEmpty)) {
        return Optional.empty();
      }
      LocalDate earliestPriceDate =
          priceDates.stream().map(Optional::orElseThrow).min(naturalOrder()).orElse(day);
      if (earliestPriceDate.equals(day)) {
        return Optional.of(day);
      }
      candidate = earliestPriceDate;
    }
    return Optional.empty();
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
