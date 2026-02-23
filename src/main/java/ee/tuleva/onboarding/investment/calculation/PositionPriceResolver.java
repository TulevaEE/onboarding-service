package ee.tuleva.onboarding.investment.calculation;

import static ee.tuleva.onboarding.investment.calculation.ValidationStatus.NO_PRICE_DATA;
import static ee.tuleva.onboarding.investment.calculation.ValidationStatus.OK;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.PriorityPriceProvider;
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.FundTicker;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PositionPriceResolver {

  private final PriorityPriceProvider priorityPriceProvider;

  public Optional<ResolvedPrice> resolve(String isin, LocalDate date) {
    return resolve(isin, date, null);
  }

  public Optional<ResolvedPrice> resolve(String isin, LocalDate date, Instant updatedBefore) {
    if (FundTicker.findByIsin(isin).isEmpty()) {
      return Optional.empty();
    }

    return Optional.of(
        priorityPriceProvider
            .resolve(isin, date, updatedBefore)
            .map(this::toResolvedPrice)
            .orElseGet(PositionPriceResolver::noPriceData));
  }

  private ResolvedPrice toResolvedPrice(FundValue fundValue) {
    return ResolvedPrice.builder()
        .usedPrice(fundValue.value())
        .priceSource(PriceSource.fromProviderName(fundValue.provider()))
        .validationStatus(OK)
        .priceDate(fundValue.date())
        .build();
  }

  private static ResolvedPrice noPriceData() {
    return ResolvedPrice.builder().validationStatus(NO_PRICE_DATA).build();
  }
}
