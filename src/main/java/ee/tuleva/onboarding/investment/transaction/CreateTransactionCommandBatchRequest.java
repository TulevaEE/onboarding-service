package ee.tuleva.onboarding.investment.transaction;

import ee.tuleva.onboarding.fund.TulevaFund;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public record CreateTransactionCommandBatchRequest(
    @Nullable List<TulevaFund> funds, @NotNull TransactionMode mode, @NotNull LocalDate asOfDate) {}
