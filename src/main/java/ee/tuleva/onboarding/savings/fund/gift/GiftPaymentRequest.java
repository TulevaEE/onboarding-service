package ee.tuleva.onboarding.savings.fund.gift;

import ee.tuleva.onboarding.payment.PaymentData.PaymentChannel;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** What an anonymous visitor sends to start paying: how much, and from which bank. */
public record GiftPaymentRequest(
    @NotNull BigDecimal amount, @NotNull PaymentChannel paymentChannel) {}
