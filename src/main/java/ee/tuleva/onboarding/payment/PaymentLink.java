package ee.tuleva.onboarding.payment;

import javax.validation.constraints.NotNull;

public record PaymentLink(@NotNull String url) {}
