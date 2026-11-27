package ee.tuleva.onboarding.investment.transaction;

import java.math.BigDecimal;
import java.util.List;
import org.jspecify.annotations.Nullable;

public sealed interface PositionReconciliationResult {

  record Compared(List<HoldingDifference> differences) implements PositionReconciliationResult {}

  record RegistryHasNoHoldings(List<HoldingDifference> differences)
      implements PositionReconciliationResult {}

  record ReportHasNoSecurities() implements PositionReconciliationResult {}

  record ReportRowsWithoutQuantity(List<String> isins) implements PositionReconciliationResult {}

  record HoldingDifference(
      String isin, @Nullable BigDecimal registryQuantity, @Nullable BigDecimal reportQuantity) {}
}
