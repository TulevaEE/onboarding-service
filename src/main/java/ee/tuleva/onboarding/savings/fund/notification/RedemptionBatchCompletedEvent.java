package ee.tuleva.onboarding.savings.fund.notification;

import java.math.BigDecimal;

public record RedemptionBatchCompletedEvent(
    int requestCount, int payoutCount, int heldCount, BigDecimal totalCashAmount, BigDecimal nav) {}
