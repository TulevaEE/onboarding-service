package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.ERROR;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.OK;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.PENDING_EXECUTION;

import ee.tuleva.onboarding.investment.transaction.FtConfirmation;
import ee.tuleva.onboarding.investment.transaction.FtVerificationStatus;
import ee.tuleva.onboarding.investment.transaction.TransactionExecution;
import ee.tuleva.onboarding.investment.transaction.TransactionExecutionRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;

@NullMarked
@Component
@RequiredArgsConstructor
class FtQuantityCheck {

  private static final BigDecimal QUANTITY_TOLERANCE = BigDecimal.ONE;

  private final TransactionExecutionRepository executionRepository;
  private final FtExecutionWait executionWait;

  FtVerificationStatus check(
      FtConfirmation confirmation, TransactionOrder order, Map<String, String> details) {
    boolean orderQuantityOk = checkOrderQuantity(confirmation, order, details);

    Optional<BigDecimal> executedQuantity = executedQuantity(order);
    if (executedQuantity.isEmpty()) {
      MissingExecution missing = executionWait.assess(confirmation.tradeDate());
      executedQuantity =
          missing.awaitsSebReport() ? Optional.empty() : executedQuantityReadAfterTheReport(order);
      if (executedQuantity.isEmpty()) {
        details.putAll(missing.details());
        return missing.awaitsSebReport() && orderQuantityOk ? PENDING_EXECUTION : ERROR;
      }
    }

    boolean executedQuantityOk =
        checkExecutedQuantity(confirmation, executedQuantity.get(), details);
    return orderQuantityOk && executedQuantityOk ? OK : ERROR;
  }

  static boolean withinQuantityTolerance(BigDecimal ftQuantity, BigDecimal quantity) {
    return ftQuantity.subtract(quantity).abs().compareTo(QUANTITY_TOLERANCE) <= 0;
  }

  private static boolean checkOrderQuantity(
      FtConfirmation confirmation, TransactionOrder order, Map<String, String> details) {
    BigDecimal orderQuantity = order.getOrderQuantity();
    if (orderQuantity == null) {
      details.put("orderQuantityMissing", "order has no quantity");
      return false;
    }
    details.put("orderQuantity", orderQuantity.toPlainString());
    return withinQuantityTolerance(confirmation.quantity(), orderQuantity);
  }

  private static boolean checkExecutedQuantity(
      FtConfirmation confirmation, BigDecimal executedQuantity, Map<String, String> details) {
    details.put("executedQuantity", executedQuantity.toPlainString());
    return withinQuantityTolerance(confirmation.quantity(), executedQuantity);
  }

  private Optional<BigDecimal> executedQuantityReadAfterTheReport(TransactionOrder order) {
    return executedQuantity(order);
  }

  private Optional<BigDecimal> executedQuantity(TransactionOrder order) {
    BigDecimal totalExecuted =
        executionRepository.findAllByOrderId(order.getId()).stream()
            .map(TransactionExecution::getExecutedQuantity)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    return totalExecuted.signum() > 0 ? Optional.of(totalExecuted) : Optional.empty();
  }
}
