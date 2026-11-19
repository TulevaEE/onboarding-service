package ee.tuleva.onboarding.ariregister;

import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

public record AnnualReport(@Nullable LocalDate periodStart, @Nullable LocalDate periodEnd) {}
