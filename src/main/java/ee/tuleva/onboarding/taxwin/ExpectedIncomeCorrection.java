package ee.tuleva.onboarding.taxwin;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

record ExpectedIncomeCorrection(@NotNull BigDecimal amount) {}
