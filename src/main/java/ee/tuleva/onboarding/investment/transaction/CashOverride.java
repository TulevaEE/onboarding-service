package ee.tuleva.onboarding.investment.transaction;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import org.jspecify.annotations.NullMarked;

@NullMarked
public record CashOverride(
    @NotNull @PositiveOrZero BigDecimal amount, @NotBlank @Size(max = 500) String comment) {}
