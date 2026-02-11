package ee.tuleva.onboarding.savings.fund.notification;

import java.math.BigDecimal;

public record SubscriptionBatchSentEvent(int paymentCount, BigDecimal totalAmount) {}
