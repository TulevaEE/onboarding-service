package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static java.math.BigDecimal.ZERO;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;

@Slf4j
@NullMarked
enum InPositionReport {
  SHOWN_SO_THE_REPORT_CARRIES_ITS_CASH,
  NOT_YET_SHOWN_SO_THE_ORDER_CARRIES_ITS_CASH,
  MAYBE_SHOWN_SO_A_PURCHASE_IS_RESERVED_AND_A_SALE_NOT_COUNTED;

  private static final String HISTORICAL_IMPORT_SOURCE = "HISTORICAL_IMPORT";
  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");

  static InPositionReport ofFill(
      TransactionOrder order, TransactionExecution execution, LocalDate positionDate) {
    if (HISTORICAL_IMPORT_SOURCE.equals(execution.getSource())) {
      return MAYBE_SHOWN_SO_A_PURCHASE_IS_RESERVED_AND_A_SALE_NOT_COUNTED;
    }
    LocalDate reportedDate = execution.getReportedDate();
    if (reportedDate == null) {
      warnUndated(execution, positionDate);
      return MAYBE_SHOWN_SO_A_PURCHASE_IS_RESERVED_AND_A_SALE_NOT_COUNTED;
    }
    if (reportedDate.isAfter(positionDate)) {
      return NOT_YET_SHOWN_SO_THE_ORDER_CARRIES_ITS_CASH;
    }
    if (reportedDate.isEqual(positionDate) && order.getInstrumentType() != ETF) {
      return fundFillTheReportMayBookOnlyTheNextDay();
    }
    return SHOWN_SO_THE_REPORT_CARRIES_ITS_CASH;
  }

  static InPositionReport ofUnfilledRemainder(TransactionOrder order, LocalDate positionDate) {
    return placedAfterTheReportDate(order, positionDate)
        ? NOT_YET_SHOWN_SO_THE_ORDER_CARRIES_ITS_CASH
        : mayBeFilledUnseenOrStillAwaitingALaterFundNav();
  }

  BigDecimal cashToCount(TransactionOrder order, BigDecimal value) {
    return switch (this) {
      case SHOWN_SO_THE_REPORT_CARRIES_ITS_CASH -> ZERO;
      case NOT_YET_SHOWN_SO_THE_ORDER_CARRIES_ITS_CASH -> value;
      case MAYBE_SHOWN_SO_A_PURCHASE_IS_RESERVED_AND_A_SALE_NOT_COUNTED ->
          order.getTransactionType() == BUY ? value : ZERO;
    };
  }

  private static void warnUndated(TransactionExecution execution, LocalDate positionDate) {
    log.warn(
        "Execution carries no reported date, leaving its position to the custodian report:"
            + " executionId={}, orderId={}, positionDate={}",
        execution.getId(),
        execution.getOrderId(),
        positionDate);
  }

  private static InPositionReport fundFillTheReportMayBookOnlyTheNextDay() {
    return MAYBE_SHOWN_SO_A_PURCHASE_IS_RESERVED_AND_A_SALE_NOT_COUNTED;
  }

  private static boolean placedAfterTheReportDate(TransactionOrder order, LocalDate positionDate) {
    Instant placedAt = order.getOrderTimestamp();
    return placedAt != null && placedAt.atZone(TALLINN).toLocalDate().isAfter(positionDate);
  }

  private static InPositionReport mayBeFilledUnseenOrStillAwaitingALaterFundNav() {
    return MAYBE_SHOWN_SO_A_PURCHASE_IS_RESERVED_AND_A_SALE_NOT_COUNTED;
  }
}
