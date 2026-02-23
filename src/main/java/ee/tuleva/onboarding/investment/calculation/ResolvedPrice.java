package ee.tuleva.onboarding.investment.calculation;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Builder;

@Builder
public record ResolvedPrice(
    BigDecimal usedPrice,
    PriceSource priceSource,
    ValidationStatus validationStatus,
    LocalDate priceDate,
    String storageKey) {}
