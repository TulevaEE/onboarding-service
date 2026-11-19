package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.ERROR;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.OK;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.PENDING_EXECUTION;

import ee.tuleva.onboarding.investment.calendar.Target2Calendar;
import ee.tuleva.onboarding.investment.transaction.FtConfirmation;
import ee.tuleva.onboarding.investment.transaction.FtVerificationStatus;
import ee.tuleva.onboarding.investment.transaction.TransactionExecution;
import ee.tuleva.onboarding.investment.transaction.TransactionExecutionRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
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
  private static final int EXECUTION_PENDING_BUSINESS_DAYS = 1;

  private final TransactionExecutionRepository executionRepository;
  private final Target2Calendar target2Calendar;
  private final Clock clock;

  FtVerificationStatus check(
      FtConfirmation confirmation, TransactionOrder order, Map<String, String> details) {
    boolean orderQuantityOk = checkOrderQuantity(confirmation, order, details);

    Optional<BigDecimal> executedQuantity = executedQuantity(order);
    if (executedQuantity.isEmpty()) {
      LocalDate executionDeadline =
          target2Calendar.addBusinessDays(
              confirmation.tradeDate(), EXECUTION_PENDING_BUSINESS_DAYS);
      if (!LocalDate.now(clock).isAfter(executionDeadline)) {
        details.put("executionPendingUntil", executionDeadline.toString());
        return orderQuantityOk ? PENDING_EXECUTION : ERROR;
      }
      details.put("executionMissing", "no execution after " + executionDeadline);
      return ERROR;
    }

    details.put("executedQuantity", executedQuantity.get().toPlainString());
    boolean executedQuantityOk =
        withinQuantityTolerance(confirmation.quantity(), executedQuantity.get());
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

  private Optional<BigDecimal> executedQuantity(TransactionOrder order) {
    BigDecimal totalExecuted =
        executionRepository.findAllByOrderId(order.getId()).stream()
            .map(TransactionExecution::getExecutedQuantity)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    return totalExecuted.signum() > 0 ? Optional.of(totalExecuted) : Optional.empty();
  }
}
