package ee.tuleva.onboarding.investment.transaction.portfolio;

import ee.tuleva.onboarding.investment.transaction.TransactionExecution;
import ee.tuleva.onboarding.investment.transaction.TransactionType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CostBasisCalculator {

  private static final int AVG_UNIT_COST_SCALE = 8;
  private static final int TOTAL_COST_SCALE = 2;
  private static final int QUANTITY_SCALE = 4;

  public PortfolioCostBasis calculate(
      Optional<PriorPosition> prior,
      List<ExecutionEvent> executions,
      String fundIsin,
      String instrumentIsin,
      LocalDate asOfDate) {
    BigDecimal priorQty = prior.map(PriorPosition::quantity).orElse(BigDecimal.ZERO);
    BigDecimal priorAvg = prior.map(PriorPosition::avgUnitCost).orElse(BigDecimal.ZERO);

    BigDecimal qty = priorQty;
    BigDecimal totalCost = priorQty.multiply(priorAvg);

    for (ExecutionEvent event : executions) {
      BigDecimal execQty = nullSafe(event.quantity());
      BigDecimal execPrice = nullSafe(event.unitPrice());
      BigDecimal commission = nullSafe(event.commission());

      if (event.side() == TransactionType.BUY) {
        totalCost = totalCost.add(execQty.multiply(execPrice)).add(commission);
        qty = qty.add(execQty);
      } else {
        BigDecimal avgBefore =
            qty.signum() == 0
                ? BigDecimal.ZERO
                : totalCost.divide(qty, AVG_UNIT_COST_SCALE, RoundingMode.HALF_UP);
        totalCost = totalCost.subtract(avgBefore.multiply(execQty));
        qty = qty.subtract(execQty);
        if (qty.signum() < 0) {
          log.warn(
              "SELL clamped to zero: fundIsin={}, instrumentIsin={}, asOfDate={}, sellQty={},"
                  + " priorQty={}",
              fundIsin,
              instrumentIsin,
              asOfDate,
              execQty,
              priorQty);
          qty = BigDecimal.ZERO;
          totalCost = BigDecimal.ZERO;
        }
      }
    }

    if (totalCost.signum() < 0) {
      log.warn(
          "Negative totalCost clamped to zero: fundIsin={}, instrumentIsin={}, asOfDate={},"
              + " totalCost={}",
          fundIsin,
          instrumentIsin,
          asOfDate,
          totalCost);
      totalCost = BigDecimal.ZERO;
    }

    BigDecimal avg =
        qty.signum() == 0
            ? BigDecimal.ZERO
            : totalCost.divide(qty, AVG_UNIT_COST_SCALE, RoundingMode.HALF_UP);

    return PortfolioCostBasis.builder()
        .fundIsin(fundIsin)
        .instrumentIsin(instrumentIsin)
        .asOfDate(asOfDate)
        .quantity(qty.setScale(QUANTITY_SCALE, RoundingMode.HALF_UP))
        .avgUnitCost(avg.setScale(AVG_UNIT_COST_SCALE, RoundingMode.HALF_UP))
        .totalCost(totalCost.setScale(TOTAL_COST_SCALE, RoundingMode.HALF_UP))
        .deltaQuantity(qty.subtract(priorQty).setScale(QUANTITY_SCALE, RoundingMode.HALF_UP))
        .source("DERIVED")
        .build();
  }

  private static BigDecimal nullSafe(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }

  public record PriorPosition(BigDecimal quantity, BigDecimal avgUnitCost) {

    public static PriorPosition from(PortfolioCostBasis row) {
      return new PriorPosition(row.getQuantity(), row.getAvgUnitCost());
    }

    public static PriorPosition from(PortfolioBaselineEntry entry) {
      return new PriorPosition(entry.getQuantity(), entry.getAvgUnitCost());
    }
  }

  public record ExecutionEvent(
      TransactionType side, BigDecimal quantity, BigDecimal unitPrice, BigDecimal commission) {

    public static ExecutionEvent of(TransactionExecution execution, TransactionType side) {
      return new ExecutionEvent(
          side,
          execution.getExecutedQuantity(),
          execution.getUnitPrice(),
          execution.getCommissionAmount());
    }
  }
}
