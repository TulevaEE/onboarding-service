package ee.tuleva.onboarding.payment;

import jakarta.validation.constraints.NotNull;

public record PaymentLink(@NotNull String url) {}
