package ee.tuleva.onboarding.investment.calculation;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Builder;

@Builder
public record ResolvedPrice(
    BigDecimal eodhdPrice,
    BigDecimal yahooPrice,
    BigDecimal usedPrice,
    PriceSource priceSource,
    ValidationStatus validationStatus,
    BigDecimal discrepancyPercent,
    LocalDate priceDate) {}
