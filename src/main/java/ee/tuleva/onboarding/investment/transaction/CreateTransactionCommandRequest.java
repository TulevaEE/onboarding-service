package ee.tuleva.onboarding.investment.transaction;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.Map;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public record CreateTransactionCommandRequest(
    @NotNull TulevaFund fund,
    @NotNull TransactionMode mode,
    @NotNull LocalDate asOfDate,
    @Nullable Map<String, Object> manualAdjustments,
    @Nullable @Valid CashOverride cash) {}
