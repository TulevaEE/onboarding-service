package ee.tuleva.onboarding.investment.transaction;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public record CreateTransactionCommandBatchRequest(
    @Nullable List<TulevaFund> funds,
    @NotNull TransactionMode mode,
    @NotNull LocalDate asOfDate,
    @Nullable Map<TulevaFund, @PositiveOrZero BigDecimal> cash) {}
