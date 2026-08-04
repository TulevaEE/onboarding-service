package ee.tuleva.onboarding.savings.fund.taxreport;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.Builder;

@Builder
public record RealisedGain(
    Instant time,
    BigDecimal units,
    BigDecimal acquisitionCost,
    BigDecimal proceeds,
    BigDecimal gain) {}
