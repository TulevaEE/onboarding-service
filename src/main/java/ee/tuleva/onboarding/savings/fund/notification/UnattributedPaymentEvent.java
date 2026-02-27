package ee.tuleva.onboarding.savings.fund.notification;

import java.math.BigDecimal;
import java.util.UUID;

public record UnattributedPaymentEvent(UUID paymentId, BigDecimal amount, String returnReason) {}
