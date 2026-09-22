package ee.tuleva.onboarding.investment.transaction.ingest;

import ee.tuleva.onboarding.investment.transaction.InstrumentType;
import ee.tuleva.onboarding.investment.transaction.TransactionExecution;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import ee.tuleva.onboarding.investment.transaction.TransactionType;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
class ReportedQuantityNormalizer {

  private static final MathContext SEB_REPORTED_PRECISION = new MathContext(10);
  private static final BigDecimal HALF = new BigDecimal("0.5");

  SebPendingTransactionRow normalize(
      TransactionOrder order,
      SebPendingTransactionRow row,
      List<TransactionExecution> existingExecutions) {
    if (!isQuantityDriven(order)) {
      return row;
    }
    BigDecimal ordered = order.getOrderQuantity();
    BigDecimal reported = row.quantity();
    if (ordered == null || reported == null || ordered.signum() <= 0) {
      return row;
    }
    BigDecimal otherPieces = sumOtherPieces(existingExecutions, row.ourRef());
    BigDecimal cumulative = otherPieces.add(reported);
    if (cumulative.compareTo(ordered) == 0 || !withinReportedRounding(cumulative, ordered)) {
      return row;
    }
    BigDecimal remainder = ordered.subtract(otherPieces);
    if (remainder.signum() <= 0) {
      return row;
    }
    return row.withQuantity(remainder);
  }

  private static boolean isQuantityDriven(TransactionOrder order) {
    return order.getInstrumentType() == InstrumentType.ETF
        || order.getTransactionType() == TransactionType.SELL;
  }

  private static boolean withinReportedRounding(BigDecimal cumulative, BigDecimal ordered) {
    return cumulative.subtract(ordered).abs().compareTo(halfUlpAtReportedPrecision(ordered)) <= 0;
  }

  private static BigDecimal halfUlpAtReportedPrecision(BigDecimal value) {
    int integerDigits = value.precision() - value.scale();
    int ulpScale = SEB_REPORTED_PRECISION.getPrecision() - integerDigits;
    return BigDecimal.ONE.movePointLeft(ulpScale).multiply(HALF);
  }

  private static BigDecimal sumOtherPieces(
      List<TransactionExecution> executions, @Nullable String currentBrokerRef) {
    return executions.stream()
        .filter(
            e -> currentBrokerRef == null || !currentBrokerRef.equals(e.getBrokerTransactionId()))
        .map(TransactionExecution::getExecutedQuantity)
        .filter(Objects::nonNull)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }
}
