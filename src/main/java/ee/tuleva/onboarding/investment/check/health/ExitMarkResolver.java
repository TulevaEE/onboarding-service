package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.comparisons.fundvalue.ValidationStatus.OK;

import ee.tuleva.onboarding.comparisons.fundvalue.PositionPriceResolver;
import ee.tuleva.onboarding.comparisons.fundvalue.ResolvedPrice;
import ee.tuleva.onboarding.instrument.InstrumentReference;
import ee.tuleva.onboarding.instrument.InstrumentReferenceService;
import ee.tuleva.onboarding.investment.check.health.ExitMark.PublishedPrice;
import ee.tuleva.onboarding.investment.transaction.ExecutedPrice;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class ExitMarkResolver {

  private static final String MUTUAL_FUND = "FUND";

  private final PositionPriceResolver positionPriceResolver;
  private final InstrumentReferenceService instrumentReferenceService;

  Map<String, ExitMark> resolve(LocalDate navDate, Map<String, ExecutedPrice> executedSells) {
    var exitMarks = new HashMap<String, ExitMark>();
    for (var executedSell : executedSells.entrySet()) {
      var isin = executedSell.getKey();
      var executedPrice = executedSell.getValue();
      exitMarks.put(
          isin, new ExitMark(executedPrice.price(), published(isin, navDate, executedPrice)));
    }
    return Map.copyOf(exitMarks);
  }

  private @Nullable PublishedPrice published(
      String isin, LocalDate navDate, ExecutedPrice executedPrice) {
    if (isMutualFund(isin)) {
      return dealingNav(isin, navDate);
    }
    return closingPrice(isin, executedPrice.tradeDate());
  }

  private @Nullable PublishedPrice dealingNav(String isin, LocalDate navDate) {
    var latestBeforeNavDate = navDate.minusDays(1);
    return positionPriceResolver
        .resolve(isin, latestBeforeNavDate)
        .filter(ExitMarkResolver::isUsable)
        .filter(resolved -> !resolved.priceDate().isAfter(latestBeforeNavDate))
        .map(ExitMarkResolver::toPublishedPrice)
        .orElse(null);
  }

  private @Nullable PublishedPrice closingPrice(String isin, LocalDate tradeDate) {
    return positionPriceResolver
        .resolve(isin, tradeDate)
        .filter(ExitMarkResolver::isUsable)
        .filter(resolved -> tradeDate.equals(resolved.priceDate()))
        .map(ExitMarkResolver::toPublishedPrice)
        .orElse(null);
  }

  private boolean isMutualFund(String isin) {
    return instrumentReferenceService
        .findByIsin(isin)
        .map(InstrumentReference::getInstrumentType)
        .filter(MUTUAL_FUND::equals)
        .isPresent();
  }

  private static boolean isUsable(ResolvedPrice resolved) {
    return resolved.validationStatus() == OK;
  }

  private static PublishedPrice toPublishedPrice(ResolvedPrice resolved) {
    return new PublishedPrice(resolved.usedPrice(), resolved.priceDate());
  }
}
