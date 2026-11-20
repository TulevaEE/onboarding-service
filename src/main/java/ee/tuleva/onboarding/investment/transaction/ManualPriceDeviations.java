package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.config.InvestmentParameter.MANUAL_PRICE_DEVIATION_THRESHOLD;

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
        overrides.prices().stream()
            .filter(override -> !overrides.excludedIsins().contains(override.isin()))
            .toList();
    if (considered.isEmpty()) {
      return new PriceDeviations(List.of(), List.of());
    }
    BigDecimal threshold =
        investmentParameterRepository.findLatestValue(MANUAL_PRICE_DEVIATION_THRESHOLD, asOfDate);
    List<PriceDeviation> beyondThreshold =
        considered.stream()
            .flatMap(
                override ->
                    lastIndexPrice(override.isin(), asOfDate, resolvedForTheCommandsDate)
                        .map(last -> PriceDeviation.between(override, last))
                        .stream())
            .filter(deviation -> deviation.exceeds(threshold))
            .toList();
    return new PriceDeviations(
        beyondThreshold.stream()
            .filter(deviation -> !overrides.confirmedPriceDeviations().contains(deviation.isin()))
            .toList(),
        beyondThreshold.stream()
            .filter(deviation -> overrides.confirmedPriceDeviations().contains(deviation.isin()))
            .toList());
  }

  private Optional<ResolvedPrice> lastIndexPrice(
      String isin, LocalDate asOfDate, Map<String, ResolvedPrice> resolvedForTheCommandsDate) {
    return Optional.ofNullable(resolvedForTheCommandsDate.get(isin))
        .or(() -> positionPriceResolver.resolveAnyAge(isin, asOfDate))
        .filter(price -> price.usedPrice() != null && price.usedPrice().signum() > 0);
  }
}
