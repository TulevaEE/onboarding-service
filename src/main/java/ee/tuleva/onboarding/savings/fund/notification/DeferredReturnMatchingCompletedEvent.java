package ee.tuleva.onboarding.savings.fund.notification;

import java.math.BigDecimal;

public record DeferredReturnMatchingCompletedEvent(
    int matchedCount, int unmatchedCount, BigDecimal totalAmount) {}
