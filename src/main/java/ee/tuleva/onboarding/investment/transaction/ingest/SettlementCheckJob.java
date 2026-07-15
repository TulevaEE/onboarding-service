package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.JobRunSchedule.TIMEZONE;
import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportType.PENDING_TRANSACTIONS;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.EXECUTED;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.SENT;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static java.util.stream.Collectors.toSet;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.event.RunOverdueSettlementRequested;
import ee.tuleva.onboarding.investment.report.InvestmentReport;
import ee.tuleva.onboarding.investment.report.InvestmentReportService;
import ee.tuleva.onboarding.investment.transaction.InstrumentType;
import ee.tuleva.onboarding.investment.transaction.SettlementDateCalculator;
import ee.tuleva.onboarding.investment.transaction.TransactionExecution;
import ee.tuleva.onboarding.investment.transaction.TransactionExecutionRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import ee.tuleva.onboarding.investment.transaction.TransactionOrderRepository;
import ee.tuleva.onboarding.investment.transaction.ingest.UnmatchedPendingTransactionFinder.InconsistentMatchedRow;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile({"production", "staging"})
@RequiredArgsConstructor
class SettlementCheckJob {

  private static final ZoneId TALLINN = ZoneId.of(TIMEZONE);
  private static final int ETF_THRESHOLD_BUSINESS_DAYS = 3;
  private static final int FUND_THRESHOLD_BUSINESS_DAYS = 5;
  private static final List<TulevaFund> FUND_ORDER =
      List.of(TulevaFund.TUK75, TulevaFund.TUK00, TulevaFund.TUV100, TulevaFund.TKF100);

  @Value("${transaction-registry.settlement-check.scan-lookback-days:60}")
  private int scanLookbackDays = 60;

  private final Clock clock;
  private final PublicHolidays publicHolidays;
  private final SettlementDateCalculator settlementDateCalculator;
  private final TransactionOrderRepository orderRepository;
  private final TransactionExecutionRepository executionRepository;
  private final InvestmentReportService reportService;
  private final SebPendingTransactionExtractor extractor;
  private final UnmatchedPendingTransactionFinder unmatchedFinder;
  private final SebClientNameToFundResolver fundResolver;
  private final OperationsNotificationService notificationService;

  @Scheduled(cron = "0 0 10 * * *", zone = TIMEZONE)
  @SchedulerLock(name = "SettlementCheckJob", lockAtMostFor = "10m", lockAtLeastFor = "1m")
  public void run() {
    LocalDate today = LocalDate.now(clock);

    var latestReport = reportService.getLatestReport(SEB, PENDING_TRANSACTIONS);
    boolean fresh = latestReport.map(report -> isUsable(report, today)).orElse(false);
    LocalDate referenceDate = fresh ? latestReport.get().getReportDate() : today;
    if (!fresh) {
      log.warn("Falling back to wall-clock for overdue check: today={}", today);
    }

    List<SebPendingTransactionRow> rows = latestReport.map(extractor::extract).orElseGet(List::of);
    Set<UUID> reportClientRefs =
        rows.stream()
            .map(SebPendingTransactionRow::clientRef)
            .filter(Objects::nonNull)
            .collect(toSet());
    Set<String> reportOurRefs =
        rows.stream()
            .map(SebPendingTransactionRow::ourRef)
            .filter(Objects::nonNull)
            .collect(toSet());

    Instant since = today.minusDays(scanLookbackDays).atStartOfDay(TALLINN).toInstant();
    List<TransactionOrder> registryOrders =
        orderRepository.findByOrderStatusInAndOrderTimestampSince(List.of(SENT, EXECUTED), since);

    List<OverdueLine> overdue =
        collectOverdue(referenceDate, fresh, reportClientRefs, reportOurRefs, registryOrders);
    List<SebPendingTransactionRow> unmatched =
        fresh ? unmatchedFinder.collectUnmatched(latestReport.get()) : List.of();
    List<InconsistentMatchedRow> inconsistent =
        fresh ? unmatchedFinder.collectInconsistent(latestReport.get()) : List.of();

    boolean emptyRegistryBootstrap = registryOrders.isEmpty();
    // TODO: drop emptyRegistryBootstrap once the registry holds real orders — it silences the
    // stale-report warning during bootstrap but would also mask a broken pipeline on an empty
    // table.
    if (overdue.isEmpty()
        && unmatched.isEmpty()
        && inconsistent.isEmpty()
        && (fresh || emptyRegistryBootstrap)) {
      log.info(
          "Settlement check clean: today={}, fresh={}, registryEmpty={}",
          today,
          fresh,
          emptyRegistryBootstrap);
      return;
    }

    String message = buildMessage(today, fresh, overdue, unmatched, inconsistent);
    notificationService.sendMessage(message, INVESTMENT);
    log.info(
        "Sent settlement check digest: today={}, overdue={}, unmatched={}, inconsistent={},"
            + " fresh={}",
        today,
        overdue.size(),
        unmatched.size(),
        inconsistent.size(),
        fresh);
  }

  @EventListener(classes = RunOverdueSettlementRequested.class)
  void onOverdueSettlementRequested() {
    run();
  }

  private boolean isUsable(InvestmentReport report, LocalDate today) {
    boolean recentEnough =
        !report.getReportDate().isBefore(publicHolidays.previousWorkingDay(today));
    return recentEnough && hasParsedHeader(report);
  }

  private static boolean hasParsedHeader(InvestmentReport report) {
    Object asOfDate = report.getMetadata().get("asOfDate");
    return asOfDate != null && !asOfDate.toString().isBlank();
  }

  private List<OverdueLine> collectOverdue(
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
    if (order.getOrderTimestamp() == null || order.getInstrumentType() == null) {
      return null;
    }
    InstrumentType instrumentType = order.getInstrumentType();
    return settlementDateCalculator.addBusinessDays(
        orderDate(order), instrumentType, order.getInstrumentIsin(), thresholdFor(instrumentType));
  }

  private @Nullable LocalDate executedDeadline(
      TransactionOrder order, List<TransactionExecution> executions) {
    Optional<LocalDate> latestSettlement = latestPieceSettlementDate(executions);
    return latestSettlement.isPresent() ? latestSettlement.get() : sentDeadline(order);
  }

  private static Optional<LocalDate> latestPieceSettlementDate(
      List<TransactionExecution> executions) {
    return executions.stream()
        .map(TransactionExecution::getActualSettlementDate)
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

  private static LocalDate orderDate(TransactionOrder order) {
    return order.getOrderTimestamp().atZone(TALLINN).toLocalDate();
  }

  private String buildMessage(
      LocalDate today,
      boolean fresh,
      List<OverdueLine> overdue,
      List<SebPendingTransactionRow> unmatched,
      List<InconsistentMatchedRow> inconsistent) {
    Map<TulevaFund, List<OverdueLine>> overdueByFund =
        overdue.stream().collect(Collectors.groupingBy(line -> line.order().getFund()));
    Map<TulevaFund, List<SebPendingTransactionRow>> unmatchedByFund = new LinkedHashMap<>();
    List<SebPendingTransactionRow> unresolved = new ArrayList<>();
    for (SebPendingTransactionRow row : unmatched) {
      fundResolver
          .resolve(row.clientName())
          .ifPresentOrElse(
              fund -> unmatchedByFund.computeIfAbsent(fund, k -> new ArrayList<>()).add(row),
              () -> unresolved.add(row));
    }
    Map<TulevaFund, List<InconsistentMatchedRow>> inconsistentByFund =
        inconsistent.stream().collect(Collectors.groupingBy(entry -> entry.order().getFund()));

    StringBuilder message = new StringBuilder();
    message.append("⚠️ SEB arveldus- ja tehingukontroll – ").append(today);
    if (!fresh) {
      message.append(
          "\n\nHOIATUS: värske SEB ootel-tehingute raport puudub või on aegunud —"
              + " arveldatud staatust ei saa tuvastada, kõik hilinenud täidetud tellimused"
              + " on allpool loetletud.");
    }

    Set<TulevaFund> fundsToReport = new LinkedHashSet<>(FUND_ORDER);
    fundsToReport.addAll(overdueByFund.keySet());
    fundsToReport.addAll(unmatchedByFund.keySet());
    fundsToReport.addAll(inconsistentByFund.keySet());
    for (TulevaFund fund : fundsToReport) {
      appendFundBlock(
          message,
          fund.getCode(),
          unmatchedByFund.getOrDefault(fund, List.of()),
          overdueByFund.getOrDefault(fund, List.of()),
          inconsistentByFund.getOrDefault(fund, List.of()));
    }
    appendFundBlock(message, "Tundmatu fond / lahendamata", unresolved, List.of(), List.of());

    return message.toString();
  }

  private void appendFundBlock(
      StringBuilder message,
      String fundLabel,
      List<SebPendingTransactionRow> unmatched,
      List<OverdueLine> overdue,
      List<InconsistentMatchedRow> inconsistent) {
    if (unmatched.isEmpty() && overdue.isEmpty() && inconsistent.isEmpty()) {
      return;
    }
    message.append("\n\n").append(fundLabel);
    if (!unmatched.isEmpty()) {
      message.append("\n  Matchimata tehingud (").append(unmatched.size()).append("):");
      for (SebPendingTransactionRow row : unmatched) {
        message
            .append("\n    - ISIN: ")
            .append(row.isin())
            .append(", kogus: ")
            .append(row.quantity())
            .append(", suund: ")
            .append(row.side())
            .append(", Our ref: ")
            .append(row.ourRef())
            .append(", kauplemiskuupäev: ")
            .append(tradeDate(row));
      }
    }
    if (!overdue.isEmpty()) {
      message.append("\n  Hilinenud arveldused (").append(overdue.size()).append("):");
      for (OverdueLine line : overdue) {
        TransactionOrder order = line.order();
        String tag =
            line.status() == SENT ? "SAADETUD, täitmist pole" : "TÄIDETUD, arveldus hilinenud";
        message
            .append("\n    - [")
            .append(tag)
            .append("] Order ")
            .append(order.getId())
            .append(", ISIN: ")
            .append(order.getInstrumentIsin())
            .append(", saadetud: ")
            .append(order.getOrderTimestamp() == null ? "?" : orderDate(order))
            .append(", tähtaeg: ")
            .append(line.deadline());
      }
    }
    if (!inconsistent.isEmpty()) {
      message.append("\n  Ebakõlalised vastavused (").append(inconsistent.size()).append("):");
      for (InconsistentMatchedRow entry : inconsistent) {
        message
            .append("\n    - Order ")
            .append(entry.order().getId())
            .append(", põhjus: ")
            .append(entry.reason())
            .append(", ISIN: ")
            .append(entry.row().isin())
            .append(", Our ref: ")
            .append(entry.row().ourRef())
            .append(", Client ref: ")
            .append(entry.row().clientRef());
      }
    }
  }

  private static @Nullable LocalDate tradeDate(SebPendingTransactionRow row) {
    return row.tradeDate() == null ? null : row.tradeDate().atZone(TALLINN).toLocalDate();
  }

  private record OverdueLine(
      TransactionOrder order,
      ee.tuleva.onboarding.investment.transaction.OrderStatus status,
      LocalDate deadline) {}
}
