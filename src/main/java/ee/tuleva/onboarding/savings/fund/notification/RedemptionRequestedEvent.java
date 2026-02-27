package ee.tuleva.onboarding.savings.fund.notification;

import java.math.BigDecimal;
import java.util.UUID;

public record RedemptionRequestedEvent(
    UUID redemptionRequestId, long userId, BigDecimal requestedAmount, BigDecimal fundUnits) {}
