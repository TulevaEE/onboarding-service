package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.ERROR;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.OK;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.PENDING_NAV;

import ee.tuleva.onboarding.comparisons.fundvalue.PositionPriceResolver;
import ee.tuleva.onboarding.comparisons.fundvalue.ResolvedPrice;
import ee.tuleva.onboarding.comparisons.fundvalue.ValidationStatus;
import ee.tuleva.onboarding.investment.transaction.FtVerificationStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@NullMarked
@Component
@RequiredArgsConstructor
class FtPriceCheck {

  private static final BigDecimal DEFAULT_PRICE_TOLERANCE = new BigDecimal("0.001");

  private final PositionPriceResolver positionPriceResolver;
  private final PriceValidator priceValidator;

  @Value("${investment.transaction.ft-confirmation.price-tolerance:0.001}")
  private BigDecimal priceTolerance = DEFAULT_PRICE_TOLERANCE;

  FtVerificationStatus check(
      String isin, LocalDate tradeDate, BigDecimal price, Map<String, String> details) {
    Optional<BigDecimal> referencePrice =
        positionPriceResolver
            .resolve(isin, tradeDate)
            .filter(resolved -> resolved.validationStatus() == ValidationStatus.OK)
            .filter(resolved -> tradeDate.equals(resolved.priceDate()))
            .map(ResolvedPrice::usedPrice);

    if (referencePrice.isEmpty()) {
      return PENDING_NAV;
    }

    BigDecimal reference = referencePrice.get();
    details.put("referencePrice", reference.toPlainString());
    if (reference.signum() <= 0) {
      details.put("invalidReferencePrice", "true");
      return ERROR;
    }
    if (priceValidator.isWithinTolerance(price, reference, priceTolerance)) {
      return OK;
    }
    details.put(
        "priceDeltaPercent", priceValidator.computeDeltaPercent(price, reference).toPlainString());
    return ERROR;
  }
}
