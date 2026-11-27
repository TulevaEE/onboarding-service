package ee.tuleva.onboarding.investment.transaction;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.jspecify.annotations.NullMarked;

@NullMarked
public record SendTransactionBatchesRequest(
    @NotEmpty List<@Valid @NotNull ReviewedBatch> batches) {}
