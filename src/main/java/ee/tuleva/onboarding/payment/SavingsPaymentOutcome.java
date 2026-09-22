package ee.tuleva.onboarding.payment;

import org.jspecify.annotations.Nullable;

public record SavingsPaymentOutcome(boolean paid, @Nullable String giftLinkToken) {}
