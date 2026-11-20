package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.config.InvestmentParameter.MANUAL_PRICE_DEVIATION_THRESHOLD;

import ee.tuleva.onboarding.comparisons.fundvalue.PositionPriceResolver;
import ee.tuleva.onboarding.comparisons.fundvalue.ResolvedPrice;
import ee.tuleva.onboarding.investment.config.InvestmentParameterRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
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
        overrides.prices().stream()
            .filter(override -> !overrides.excludedIsins().contains(override.isin()))
            .toList();
    requireAConsideredManualPriceForEveryConfirmation(overrides, considered);
    if (considered.isEmpty()) {
      return new PriceDeviations(List.of(), List.of(), List.of(), List.of());
    }
    BigDecimal threshold =
        investmentParameterRepository.findLatestValue(MANUAL_PRICE_DEVIATION_THRESHOLD, asOfDate);
    Map<String, ResolvedPrice> lastIndexPrices = new HashMap<>();
    considered.forEach(
        override ->
            lastIndexPrice(override.isin(), asOfDate, resolvedForTheCommandsDate)
                .ifPresent(last -> lastIndexPrices.put(override.isin(), last)));
    List<PriceDeviation> beyondThreshold =
        considered.stream()
            .flatMap(
                override ->
                    Optional.ofNullable(lastIndexPrices.get(override.isin()))
                        .map(last -> PriceDeviation.between(override, last))
                        .stream())
            .filter(deviation -> deviation.exceeds(threshold))
            .toList();
    List<PriceDeviation> unconfirmed =
        beyondThreshold.stream().filter(deviation -> !isConfirmed(deviation, overrides)).toList();
    return new PriceDeviations(
        unconfirmed,
        overrides.confirmedPriceDeviations().stream()
            .filter(
                confirmation ->
                    unconfirmed.stream()
                        .anyMatch(deviation -> deviation.isin().equals(confirmation.isin())))
            .toList(),
        beyondThreshold.stream().filter(deviation -> isConfirmed(deviation, overrides)).toList(),
        considered.stream()
            .map(PriceOverride::isin)
            .filter(isin -> !lastIndexPrices.containsKey(isin))
            .sorted()
            .toList());
  }

  private static boolean isConfirmed(PriceDeviation deviation, TradePrepOverrides overrides) {
    return overrides.confirmedPriceDeviations().stream()
        .anyMatch(confirmation -> confirmation.confirms(deviation));
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
