package ee.tuleva.onboarding.savings.fund.gift;

import ee.tuleva.onboarding.payment.PaymentData.PaymentChannel;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

public record GiftPaymentRequest(
    @NotNull BigDecimal amount,
    @NotNull PaymentChannel paymentChannel,
    @Size(max = Gift.MAX_MESSAGE_LENGTH) @Nullable String message) {}
