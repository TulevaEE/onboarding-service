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
    BigDecimal reported = row.quantity();
    if (reported == null) {
      return row;
    }
    BigDecimal stored = storedQuantity(order, reported, existingExecutions, row.ourRef());
    return stored.compareTo(reported) == 0 ? row : row.withQuantity(stored);
  }

  BigDecimal storedQuantity(
      TransactionOrder order,
      BigDecimal reported,
      List<TransactionExecution> existingExecutions,
      @Nullable String brokerRef) {
    if (!isQuantityDriven(order)) {
      return reported;
    }
    BigDecimal ordered = order.getOrderQuantity();
    if (ordered == null || ordered.signum() <= 0) {
      return reported;
    }
    BigDecimal otherPieces = sumOtherPieces(existingExecutions, brokerRef);
    BigDecimal cumulative = otherPieces.add(reported);
    if (cumulative.compareTo(ordered) == 0 || !withinReportedRounding(cumulative, ordered)) {
      return reported;
    }
    BigDecimal remainder = ordered.subtract(otherPieces);
    if (remainder.signum() <= 0) {
      return reported;
    }
    return remainder;
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
