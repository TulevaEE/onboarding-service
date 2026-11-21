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
import ee.tuleva.onboarding.investment.transaction.TradePrepPricing.FreshestPerInstrument;
import ee.tuleva.onboarding.investment.transaction.TradePrepPricing.OnCommonDate;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
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
  private final ManualPriceDeviations manualPriceDeviations;

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
    PriceDeviations deviations = manualPriceDeviations.check(asOfDate, overrides, indexPrices);
    return priced(asOfDate, overrides, unpriced, manual, indexPrices, deviations)
        .withDeviations(deviations);
  }

  private PriceCheck priced(
      LocalDate asOfDate,
      TradePrepOverrides overrides,
      List<String> unpriced,
      ManualPrices manual,
      Map<String, ResolvedPrice> indexPrices,
      PriceDeviations deviations) {
    Map<String, ResolvedPrice> unmanuallyPriced = withoutManual(indexPrices, manual);
    if (!unpriced.isEmpty()) {
      return stoppedOnTheCommandsDate(
          new MissingPrices(unpriced), manual, deviations, indexPrices, unmanuallyPriced);
    }
    if (overrides.priceDateRule() == PriceDateRule.FRESHEST_PER_INSTRUMENT
        || unmanuallyPriced.isEmpty()) {
      return passedOnTheCommandsDate(manual, deviations, indexPrices, unmanuallyPriced);
    }
    return latestCommonDate(unmanuallyPriced, asOfDate)
        .map(
            common ->
                new PriceCheck(
                    List.of(),
                    new OnCommonDate(manual.withIndexPrices(common.prices()), common.date()),
                    manual.uses(
                        deviations,
                        manualInstrumentsIndexPricesOn(
                            common.date(), indexPrices, manual, overrides.excludedIsins())),
                    List.of()))
        .orElseGet(
            () ->
                stoppedOnTheCommandsDate(
                    new NoCommonPriceDate(asOfDate.minusDays(MAX_LOOKBACK_DAYS), asOfDate),
                    manual,
                    deviations,
                    indexPrices,
                    unmanuallyPriced));
  }

  private static PriceCheck stoppedOnTheCommandsDate(
      InputGateFailure failure,
      ManualPrices manual,
      PriceDeviations deviations,
      Map<String, ResolvedPrice> indexPrices,
      Map<String, ResolvedPrice> unmanuallyPriced) {
    return new PriceCheck(
        List.of(failure),
        new FreshestPerInstrument(manual.withIndexPrices(unmanuallyPriced)),
        manual.uses(deviations, indexPrices),
        List.of());
  }

  private static PriceCheck passedOnTheCommandsDate(
      ManualPrices manual,
      PriceDeviations deviations,
      Map<String, ResolvedPrice> indexPrices,
      Map<String, ResolvedPrice> unmanuallyPriced) {
    return new PriceCheck(
        List.of(),
        new FreshestPerInstrument(manual.withIndexPrices(unmanuallyPriced)),
        manual.uses(deviations, indexPrices),
        List.of());
  }

  private Map<String, ResolvedPrice> manualInstrumentsIndexPricesOn(
      LocalDate date,
      Map<String, ResolvedPrice> resolvedForTheCommandsDate,
      ManualPrices manual,
      Set<String> excludedIsins) {
    return manual.overrides().stream()
        .map(PriceOverride::isin)
        .filter(isin -> !excludedIsins.contains(isin))
        .flatMap(
            isin ->
                Optional.ofNullable(resolvedForTheCommandsDate.get(isin))
                    .filter(price -> date.equals(price.priceDate()))
                    .or(
                        () ->
                            indexPrice(isin, date).filter(price -> date.equals(price.priceDate())))
                    .map(price -> Map.entry(isin, price))
                    .stream())
        .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private record CommonDatePrices(LocalDate date, Map<String, ResolvedPrice> prices) {}

  private Optional<CommonDatePrices> latestCommonDate(
      Map<String, ResolvedPrice> atAsOfDate, LocalDate asOfDate) {
    LocalDate earliestAllowed = asOfDate.minusDays(MAX_LOOKBACK_DAYS);
    Map<String, ResolvedPrice> prices = atAsOfDate;
    LocalDate latestPossible = asOfDate;
    while (true) {
      LocalDate earliestPriceDate =
          prices.values().stream()
              .map(ResolvedPrice::priceDate)
              .min(naturalOrder())
              .orElse(latestPossible);
      if (earliestPriceDate.isBefore(earliestAllowed)) {
        return Optional.empty();
      }
      LocalDate day = earliestPriceDate;
      if (prices.values().stream().allMatch(price -> day.equals(price.priceDate()))) {
        return Optional.of(new CommonDatePrices(day, prices));
      }
      Map<String, ResolvedPrice> onOrBefore = pricesOnOrBefore(prices.keySet(), day);
      if (onOrBefore.size() < prices.size()) {
        return Optional.empty();
      }
      prices = onOrBefore;
      latestPossible = day;
    }
  }

  private Map<String, ResolvedPrice> pricesOnOrBefore(Set<String> isins, LocalDate day) {
    return isins.stream()
        .flatMap(isin -> indexPrice(isin, day).map(price -> Map.entry(isin, price)).stream())
        .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private static Map<String, ResolvedPrice> withoutManual(
      Map<String, ResolvedPrice> indexPrices, ManualPrices manual) {
    return indexPrices.entrySet().stream()
        .filter(entry -> !manual.prices(entry.getKey()))
        .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
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
