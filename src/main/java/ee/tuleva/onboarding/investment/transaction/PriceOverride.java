package ee.tuleva.onboarding.investment.transaction;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import org.jspecify.annotations.NullMarked;

@NullMarked
public record PriceOverride(
    @NotNull @Pattern(regexp = "[A-Z]{2}[A-Z0-9]{9}[0-9]") String isin,
    @NotNull @Positive @Digits(integer = 12, fraction = 8) BigDecimal price,
    @NotBlank @Size(max = 500) String comment) {}
