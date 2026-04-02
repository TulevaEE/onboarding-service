package ee.tuleva.onboarding.comparisons.fundvalue;

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
