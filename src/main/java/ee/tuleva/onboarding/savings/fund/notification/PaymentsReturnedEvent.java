package ee.tuleva.onboarding.savings.fund.notification;

import java.math.BigDecimal;

public record PaymentsReturnedEvent(int paymentCount, BigDecimal totalAmount) {}
