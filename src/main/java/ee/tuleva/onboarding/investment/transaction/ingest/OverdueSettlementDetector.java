package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.JobRunSchedule.TIMEZONE;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.EXECUTED;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.SENT;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.investment.report.InvestmentReport;
import ee.tuleva.onboarding.investment.transaction.InstrumentType;
import ee.tuleva.onboarding.investment.transaction.OrderStatus;
import ee.tuleva.onboarding.investment.transaction.SettlementDateCalculator;
import ee.tuleva.onboarding.investment.transaction.TransactionExecution;
import ee.tuleva.onboarding.investment.transaction.TransactionExecutionRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class OverdueSettlementDetector {

  private static final ZoneId TALLINN = ZoneId.of(TIMEZONE);
  private static final int ETF_THRESHOLD_BUSINESS_DAYS = 3;
  private static final int FUND_THRESHOLD_BUSINESS_DAYS = 5;

  private final PublicHolidays publicHolidays;
  private final SettlementDateCalculator settlementDateCalculator;
  private final TransactionExecutionRepository executionRepository;

  record OverdueLine(TransactionOrder order, OrderStatus status, LocalDate deadline) {}

  boolean isUsable(InvestmentReport report, LocalDate today) {
    boolean recentEnough =
        !report.getReportDate().isBefore(publicHolidays.previousWorkingDay(today));
    return recentEnough && hasParsedHeader(report);
  }

  private static boolean hasParsedHeader(InvestmentReport report) {
    Object asOfDate = report.getMetadata().get("asOfDate");
    return asOfDate != null && !asOfDate.toString().isBlank();
  }

  List<OverdueLine> collectOverdue(
      LocalDate referenceDate,
      boolean fresh,
      Set<UUID> reportClientRefs,
      Set<String> reportOurRefs,
      List<TransactionOrder> candidates) {
    Map<Long, List<TransactionExecution>> executionsByOrderId =
        executionRepository
            .findByOrderIdIn(candidates.stream().map(TransactionOrder::getId).toList())
            .stream()
            .collect(Collectors.groupingBy(TransactionExecution::getOrderId));

    List<OverdueLine> overdue = new ArrayList<>();
    for (TransactionOrder order : candidates) {
      if (order.getOrderStatus() == SENT) {
        LocalDate deadline = sentDeadline(order);
        if (deadline != null && deadline.isBefore(referenceDate)) {
          overdue.add(new OverdueLine(order, SENT, deadline));
        }
      } else if (order.getOrderStatus() == EXECUTED) {
        List<TransactionExecution> executions =
            executionsByOrderId.getOrDefault(order.getId(), List.of());
        LocalDate deadline = executedDeadline(order, executions);
        if (deadline == null || !deadline.isBefore(referenceDate)) {
          continue;
        }
        boolean settledSinceFreshReport =
            fresh && !isPresentInReport(order, executions, reportClientRefs, reportOurRefs);
        if (settledSinceFreshReport) {
          continue;
        }
        overdue.add(new OverdueLine(order, EXECUTED, deadline));
      }
    }
    return overdue;
  }

  private @Nullable LocalDate sentDeadline(TransactionOrder order) {
    Instant orderTimestamp = order.getOrderTimestamp();
    if (orderTimestamp == null || order.getInstrumentType() == null) {
      return null;
    }
    InstrumentType instrumentType = order.getInstrumentType();
    return settlementDateCalculator.addBusinessDays(
        orderDate(orderTimestamp),
        instrumentType,
        order.getInstrumentIsin(),
        thresholdFor(instrumentType));
  }

  private @Nullable LocalDate executedDeadline(
      TransactionOrder order, List<TransactionExecution> executions) {
    Optional<LocalDate> latestSettlement = latestPieceSettlementDate(executions);
    return latestSettlement.isPresent() ? latestSettlement.get() : sentDeadline(order);
  }

  private static Optional<LocalDate> latestPieceSettlementDate(
      List<TransactionExecution> executions) {
    return executions.stream()
        .map(TransactionExecution::getScheduledSettlementDate)
        .filter(Objects::nonNull)
        .max(Comparator.naturalOrder());
  }

  private static boolean isPresentInReport(
      TransactionOrder order,
      List<TransactionExecution> executions,
      Set<UUID> reportClientRefs,
      Set<String> reportOurRefs) {
    if (order.getOrderUuid() != null && reportClientRefs.contains(order.getOrderUuid())) {
      return true;
    }
    return executions.stream()
        .map(TransactionExecution::getBrokerTransactionId)
        .filter(Objects::nonNull)
        .anyMatch(reportOurRefs::contains);
  }

  private static int thresholdFor(InstrumentType instrumentType) {
    return instrumentType == InstrumentType.ETF
        ? ETF_THRESHOLD_BUSINESS_DAYS
        : FUND_THRESHOLD_BUSINESS_DAYS;
  }

  private static LocalDate orderDate(Instant orderTimestamp) {
    return orderTimestamp.atZone(TALLINN).toLocalDate();
  }
}
