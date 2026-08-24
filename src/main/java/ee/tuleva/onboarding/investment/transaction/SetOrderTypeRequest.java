package ee.tuleva.onboarding.investment.transaction;

import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.NullMarked;

@NullMarked
public record SetOrderTypeRequest(@NotNull OrderType orderType) {}
