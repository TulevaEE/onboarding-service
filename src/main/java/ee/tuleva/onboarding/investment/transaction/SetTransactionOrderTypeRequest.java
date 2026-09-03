package ee.tuleva.onboarding.investment.transaction;

import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.NullMarked;

@NullMarked
public record SetTransactionOrderTypeRequest(@NotNull OrderType orderType) {}
