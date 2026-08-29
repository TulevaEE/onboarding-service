package ee.tuleva.onboarding.comparisons.fundvalue;

import static ee.tuleva.onboarding.comparisons.fundvalue.ValidationStatus.NO_PRICE_DATA;
import static ee.tuleva.onboarding.comparisons.fundvalue.ValidationStatus.OK;

import ee.tuleva.onboarding.instrument.InstrumentReferenceService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PositionPriceResolver {

  private final PriorityPriceProvider priorityPriceProvider;
  private final InstrumentReferenceService instrumentReferenceService;

  public Optional<ResolvedPrice> resolve(String isin, LocalDate date) {
    return resolve(isin, date, null);
  }

  public Optional<ResolvedPrice> resolve(
      String isin, LocalDate date, @Nullable Instant updatedBefore) {
    if (instrumentReferenceService.findByIsin(isin).isEmpty()) {
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
        .storageKey(fundValue.key())
        .build();
  }

  private static ResolvedPrice noPriceData() {
    return ResolvedPrice.builder().validationStatus(NO_PRICE_DATA).build();
  }
}
