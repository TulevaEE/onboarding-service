package ee.tuleva.onboarding.investment.fees.instrument;

import static ee.tuleva.onboarding.investment.fees.instrument.DayHoldings.INPUTS_AT_MOST_DAYS_BEFORE_THE_DAY;

import ee.tuleva.onboarding.comparisons.fundvalue.PriorityPriceProvider;
import ee.tuleva.onboarding.instrument.InstrumentReference;
import ee.tuleva.onboarding.instrument.InstrumentReferenceService;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class DayPriceReader {

  private final PriorityPriceProvider priorityPriceProvider;
  private final InstrumentReferenceService instrumentReferenceService;

  Optional<InstrumentPrice> priceOn(String isin, LocalDate day, Holding holding) {
    return exchangeTraded(isin)
        ? holding.priceInTheNavReport().or(() -> ownPriceOn(isin, day))
        : ownPriceOn(isin, day).or(holding::priceInTheNavReport);
  }

  private boolean exchangeTraded(String isin) {
    return instrumentReferenceService
        .findByIsin(isin)
        .map(InstrumentReference::isExchangeTraded)
        .orElse(false);
  }

  private Optional<InstrumentPrice> ownPriceOn(String isin, LocalDate day) {
    return priorityPriceProvider
        .resolve(isin, day)
        .filter(value -> !value.date().isBefore(day.minusDays(INPUTS_AT_MOST_DAYS_BEFORE_THE_DAY)))
        .map(value -> new InstrumentPrice(value.value(), value.date(), value.provider()));
  }
}
