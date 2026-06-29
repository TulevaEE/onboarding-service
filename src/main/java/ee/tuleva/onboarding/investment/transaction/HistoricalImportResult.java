package ee.tuleva.onboarding.investment.transaction;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NullMarked;

@NullMarked
public record HistoricalImportResult(
    int rowCount,
    int ordersCreated,
    int executionsCreated,
    int settlementsCreated,
    int skippedExisting,
    List<RowError> errors,
    Map<TulevaFund, BigDecimal> totalAmountByFund) {

  public record RowError(int rowNumber, String reason) {}
}
