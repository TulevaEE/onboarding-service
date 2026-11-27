package ee.tuleva.onboarding.taxwin;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

record ExpectedYearlyIncome(
    @NotNull @DecimalMin("0") @DecimalMax("10000000") @Digits(integer = 8, fraction = 2)
        BigDecimal amount) {}
