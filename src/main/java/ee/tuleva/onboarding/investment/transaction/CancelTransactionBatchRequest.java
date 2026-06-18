package ee.tuleva.onboarding.investment.transaction;

import jakarta.validation.constraints.NotBlank;
import org.jspecify.annotations.NullMarked;

@NullMarked
public record CancelTransactionBatchRequest(@NotBlank String reason) {}
