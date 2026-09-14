package ee.tuleva.onboarding.banking.payment;

import org.jspecify.annotations.Nullable;

public record PaymentRejectedEvent(String endToEndId, @Nullable String reasonCode) {}
