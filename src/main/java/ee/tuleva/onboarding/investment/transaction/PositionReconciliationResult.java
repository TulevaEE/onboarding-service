package ee.tuleva.onboarding.investment.transaction;

import java.math.BigDecimal;
import java.util.List;

public sealed interface PositionReconciliationResult {

  record Compared(List<HoldingDifference> differences) implements PositionReconciliationResult {}

  record RegistryHasNoHoldings() implements PositionReconciliationResult {}

  record ReportHasNoSecurities() implements PositionReconciliationResult {}

  record ReportRowsWithoutQuantity(List<String> isins) implements PositionReconciliationResult {}

  record HoldingDifference(String isin, BigDecimal registryQuantity, BigDecimal reportQuantity) {}
}
