package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.config.InvestmentParameter.MANUAL_PRICE_DEVIATION_THRESHOLD;
import static ee.tuleva.onboarding.investment.transaction.ManualPriceDeviationCheck.MEASURED;
import static ee.tuleva.onboarding.investment.transaction.ManualPriceDeviationCheck.NO_INDEX_PRICE_HISTORY;
import static ee.tuleva.onboarding.investment.transaction.ManualPriceDeviationCheck.SKIPPED_EXCLUDED;
import static java.util.Comparator.comparing;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toMap;

import ee.tuleva.onboarding.comparisons.fundvalue.PositionPriceResolver;
import ee.tuleva.onboarding.comparisons.fundvalue.ResolvedPrice;
import ee.tuleva.onboarding.investment.config.InvestmentParameterRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@NullMarked
class ManualPriceDeviations {

  private final PositionPriceResolver positionPriceResolver;
  private final InvestmentParameterRepository investmentParameterRepository;

  PriceDeviations check(
      LocalDate asOfDate,
      TradePrepOverrides overrides,
      Map<String, ResolvedPrice> resolvedForTheCommandsDate) {
    List<PriceOverride> considered =
        overrides.prices().stream().filter(override -> !excluded(override, overrides)).toList();
    requireAConsideredManualPriceForEveryConfirmation(overrides, considered);
    Map<String, ResolvedPrice> lastIndexPrices =
        considered.stream()
            .flatMap(
                override ->
                    lastIndexPrice(override.isin(), asOfDate, resolvedForTheCommandsDate)
                        .map(last -> Map.entry(override.isin(), last))
                        .stream())
            .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
    Map<String, ManualPriceDeviationCheck> checks =
        overrides.prices().stream()
            .collect(
                toMap(
                    PriceOverride::isin,
                    override -> checkOf(override, overrides, lastIndexPrices)));
    List<JudgedDeviation> judged =
        lastIndexPrices.isEmpty()
            ? List.of()
            : beyondThreshold(asOfDate, considered, lastIndexPrices).stream()
                .map(deviation -> JudgedDeviation.of(deviation, overrides))
                .toList();
    return new PriceDeviations(
        judged.stream()
            .filter(not(JudgedDeviation::confirmed))
            .map(JudgedDeviation::deviation)
            .toList(),
        judged.stream()
            .filter(not(JudgedDeviation::confirmed))
            .flatMap(deviation -> deviation.confirmations().stream())
            .toList(),
        judged.stream().filter(JudgedDeviation::confirmed).map(JudgedDeviation::deviation).toList(),
        checks);
  }

  private static boolean excluded(PriceOverride override, TradePrepOverrides overrides) {
    return overrides.excludedIsins().contains(override.isin());
  }

  private static ManualPriceDeviationCheck checkOf(
      PriceOverride override,
      TradePrepOverrides overrides,
      Map<String, ResolvedPrice> lastIndexPrices) {
    if (excluded(override, overrides)) {
      return SKIPPED_EXCLUDED;
    }
    return lastIndexPrices.containsKey(override.isin()) ? MEASURED : NO_INDEX_PRICE_HISTORY;
  }

  private List<PriceDeviation> beyondThreshold(
      LocalDate asOfDate,
      List<PriceOverride> considered,
      Map<String, ResolvedPrice> lastIndexPrices) {
    BigDecimal threshold =
        investmentParameterRepository.findLatestValue(MANUAL_PRICE_DEVIATION_THRESHOLD, asOfDate);
    return considered.stream()
        .flatMap(
            override ->
                Optional.ofNullable(lastIndexPrices.get(override.isin()))
                    .map(last -> PriceDeviation.between(override, last))
                    .stream())
        .filter(deviation -> deviation.exceeds(threshold))
        .toList();
  }

  private record JudgedDeviation(
      PriceDeviation deviation, List<PriceDeviationConfirmation> confirmations) {

    static JudgedDeviation of(PriceDeviation deviation, TradePrepOverrides overrides) {
      return new JudgedDeviation(
          deviation,
          overrides.confirmedPriceDeviations().stream()
              .filter(confirmation -> confirmation.isin().equals(deviation.isin()))
              .sorted(comparing(PriceDeviationConfirmation::price))
              .toList());
    }

    boolean confirmed() {
      return confirmations.stream().anyMatch(given -> given.confirms(deviation));
    }
  }

  private static void requireAConsideredManualPriceForEveryConfirmation(
      TradePrepOverrides overrides, List<PriceOverride> considered) {
    List<String> withoutConsideredManualPrice =
        overrides.confirmedPriceDeviations().stream()
            .map(PriceDeviationConfirmation::isin)
            .filter(isin -> considered.stream().noneMatch(price -> price.isin().equals(isin)))
            .sorted()
            .toList();
    if (!withoutConsideredManualPrice.isEmpty()) {
      throw new IllegalArgumentException(
          ("Price deviation confirmed for an instrument without a manual price or excluded from"
                  + " the run: isins=%s")
              .formatted(withoutConsideredManualPrice));
    }
  }

  private Optional<ResolvedPrice> lastIndexPrice(
      String isin, LocalDate asOfDate, Map<String, ResolvedPrice> resolvedForTheCommandsDate) {
    return Optional.ofNullable(resolvedForTheCommandsDate.get(isin))
        .or(() -> positionPriceResolver.resolveAnyAge(isin, asOfDate))
        .filter(price -> price.usedPrice() != null && price.usedPrice().signum() > 0);
  }
}
