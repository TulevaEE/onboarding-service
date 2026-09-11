package ee.tuleva.onboarding.investment.transaction;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

public record TradeCalculation(
    @Nullable String isin,
    BigDecimal tradeAmount,
    BigDecimal projectedWeight,
    LimitStatus limitStatus) {}
