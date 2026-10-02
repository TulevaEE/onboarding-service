package ee.tuleva.onboarding.comparisons.overview;

import static java.time.ZoneOffset.UTC;
import static java.util.Comparator.naturalOrder;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.FundValueProvider;
import ee.tuleva.onboarding.epis.CashFlow;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class BalancePriceDates {

  private final FundValueProvider fundValueProvider;

  Optional<Instant> latestPriceTime(List<CashFlow> holdings, LocalDate pricedOnOrBefore) {
    List<Optional<LocalDate>> priceDates =
        holdings.stream()
            .filter(holding -> holding.getAmount().signum() != 0)
            .map(holding -> priceDateOf(holding, pricedOnOrBefore))
            .toList();
    if (priceDates.stream().anyMatch(Optional::isEmpty)) {
      return Optional.empty();
    }
    return priceDates.stream()
        .flatMap(Optional::stream)
        .max(naturalOrder())
        .map(date -> date.atStartOfDay(UTC).toInstant());
  }

  private Optional<LocalDate> priceDateOf(CashFlow holding, LocalDate pricedOnOrBefore) {
    String isin = holding.getIsin();
    BigDecimal unitPrice = holding.getNav();
    if (isin == null || unitPrice == null) {
      return Optional.empty();
    }
    Predicate<FundValue> valuedTheHolding = price -> price.value().compareTo(unitPrice) == 0;
    Optional<FundValue> latestPrice = fundValueProvider.getLatestValue(isin, pricedOnOrBefore);
    return latestPrice
        .filter(valuedTheHolding)
        .or(() -> latestPrice.flatMap(price -> priceBefore(isin, price)).filter(valuedTheHolding))
        .map(FundValue::date);
  }

  private Optional<FundValue> priceBefore(String isin, FundValue price) {
    return fundValueProvider.getLatestValue(isin, price.date().minusDays(1));
  }
}
