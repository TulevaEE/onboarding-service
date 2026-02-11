package ee.tuleva.onboarding.savings.fund.notification;

import java.math.BigDecimal;

public record IssuingCompletedEvent(
    int paymentCount, BigDecimal totalAmount, BigDecimal totalFundUnits, BigDecimal nav) {}
