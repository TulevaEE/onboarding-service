package ee.tuleva.onboarding.savings.fund.gift;

import ee.tuleva.onboarding.payment.PaymentData.PaymentChannel;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/** What an anonymous visitor sends to start paying: how much, from which bank, and a few words. */
public record GiftPaymentRequest(
    @NotNull BigDecimal amount,
    @NotNull PaymentChannel paymentChannel,
    @Size(max = GiftMessage.MAX_LENGTH) @Nullable String message) {}
