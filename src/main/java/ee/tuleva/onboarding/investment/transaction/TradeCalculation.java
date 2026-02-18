package ee.tuleva.onboarding.investment.transaction;

import java.math.BigDecimal;

public record TradeCalculation(
    String isin, BigDecimal tradeAmount, BigDecimal projectedWeight, LimitStatus limitStatus) {}
