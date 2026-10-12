package ee.tuleva.onboarding.investment.check.limit;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

public record OwnershipBreach(
    String isin,
    String name,
    BigDecimal holdingValue,
    BigDecimal underlyingFundSize,
    BigDecimal reportedFundSize,
    String reportedCurrency,
    @Nullable LocalDate reportedUpdatedAt,
    BigDecimal actualPercent,
    BigDecimal softLimitPercent,
    BigDecimal hardLimitPercent,
    BreachSeverity severity)
    implements OwnershipAssessment {}
