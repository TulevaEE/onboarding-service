package ee.tuleva.onboarding.savings.fund.admin;

import jakarta.validation.constraints.NotNull;

public record CancelSavingsFundPaymentRequest(@NotNull String reason) {}
