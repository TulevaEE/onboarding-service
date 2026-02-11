package ee.tuleva.onboarding.savings.fund.notification;

import java.math.BigDecimal;

public record ReservationCompletedEvent(int paymentCount, BigDecimal totalAmount) {}
