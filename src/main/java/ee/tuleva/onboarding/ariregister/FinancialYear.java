package ee.tuleva.onboarding.ariregister;

import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

public record FinancialYear(
    @Nullable String start,
    @Nullable String end,
    @Nullable LocalDate validFrom,
    @Nullable LocalDate validTo) {}
