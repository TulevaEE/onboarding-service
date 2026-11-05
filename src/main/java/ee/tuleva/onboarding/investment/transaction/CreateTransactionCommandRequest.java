package ee.tuleva.onboarding.investment.transaction;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public record CreateTransactionCommandRequest(
    @NotNull TulevaFund fund,
    @NotNull TransactionMode mode,
    @NotNull LocalDate asOfDate,
    @Nullable Map<String, Object> manualAdjustments,
    @Nullable @Valid CashOverride cash,
    @Nullable Set<@NotNull @Pattern(regexp = "[A-Z]{2}[A-Z0-9]{9}[0-9]") String> excludeIsins) {}
