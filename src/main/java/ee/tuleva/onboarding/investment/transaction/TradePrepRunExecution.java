package ee.tuleva.onboarding.investment.transaction;

import java.time.Instant;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

public record TradePrepRunExecution(
    @Nullable Instant executionTimestamp, @Nullable LocalDate reportedDate) {}
