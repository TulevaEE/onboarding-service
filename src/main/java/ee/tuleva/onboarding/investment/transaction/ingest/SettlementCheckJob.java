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
import ee.tuleva.onboarding.investment.report.InvestmentReportService;
import ee.tuleva.onboarding.investment.transaction.InstrumentType;
import ee.tuleva.onboarding.investment.transaction.TransactionExecution;
import ee.tuleva.onboarding.investment.transaction.TransactionExecutionRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import ee.tuleva.onboarding.investment.transaction.TransactionOrderRepository;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
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
    boolean fresh =
        latestReport
            .map(
                report ->
                    !report.getReportDate().isBefore(publicHolidays.previousWorkingDay(today)))
            .orElse(false);

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
        collectOverdue(today, fresh, reportClientRefs, reportOurRefs, registryOrders);
    List<SebPendingTransactionRow> unmatched =
        fresh ? unmatchedFinder.collectUnmatched(latestReport.get()) : List.of();

    // TODO: remove the registryOrders.isEmpty() guard once the transaction registry holds real
    // orders. It exists only to silence the stale/missing-report warning during the empty-registry
    // bootstrap; left in place it would also hide a genuinely broken pipeline if the table is ever
    // wiped to empty.
    if (overdue.isEmpty() && unmatched.isEmpty() && (fresh || registryOrders.isEmpty())) {
      log.info(
          "Settlement check clean: today={}, fresh={}, registryEmpty={}",
          today,
          fresh,
          registryOrders.isEmpty());
      return;
    }

    String message = buildMessage(today, fresh, overdue, unmatched);
    notificationService.sendMessage(message, INVESTMENT);
    log.info(
        "Sent settlement check digest: today={}, overdue={}, unmatched={}, fresh={}",
        today,
        overdue.size(),
        unmatched.size(),
        fresh);
  }

  @EventListener(classes = RunOverdueSettlementRequested.class)
  void onOverdueSettlementRequested() {
    run();
  }

  private List<OverdueLine> collectOverdue(
      LocalDate today,
      boolean fresh,
      Set<UUID> reportClientRefs,
      Set<String> reportOurRefs,
      List<TransactionOrder> candidates) {
    Map<Long, TransactionExecution> executionsByOrderId =
        executionRepository
            .findByOrderIdIn(candidates.stream().map(TransactionOrder::getId).toList())
            .stream()
            .collect(
                Collectors.toMap(
                    TransactionExecution::getOrderId, Function.identity(), (a, b) -> a));

    List<OverdueLine> overdue = new ArrayList<>();
    for (TransactionOrder order : candidates) {
      if (order.getOrderStatus() == SENT) {
        LocalDate deadline = sentDeadline(order);
        if (deadline != null && deadline.isBefore(today)) {
          overdue.add(new OverdueLine(order, SENT, deadline));
        }
      } else if (order.getOrderStatus() == EXECUTED) {
        TransactionExecution execution = executionsByOrderId.get(order.getId());
        LocalDate deadline = executedDeadline(order, execution);
        if (deadline == null || !deadline.isBefore(today)) {
          continue;
        }
        // When the latest report is fresh, an EXECUTED order absent from it has settled -> skip.
        if (fresh && !isPresentInReport(order, execution, reportClientRefs, reportOurRefs)) {
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
    return addBusinessDays(orderDate(order), thresholdFor(order.getInstrumentType()));
  }

  private @Nullable LocalDate executedDeadline(
      TransactionOrder order, @Nullable TransactionExecution execution) {
    if (execution != null && execution.getActualSettlementDate() != null) {
      return execution.getActualSettlementDate();
    }
    return sentDeadline(order);
  }

  private static boolean isPresentInReport(
      TransactionOrder order,
      @Nullable TransactionExecution execution,
      Set<UUID> reportClientRefs,
      Set<String> reportOurRefs) {
    if (order.getOrderUuid() != null && reportClientRefs.contains(order.getOrderUuid())) {
      return true;
    }
    return execution != null
        && execution.getBrokerTransactionId() != null
        && reportOurRefs.contains(execution.getBrokerTransactionId());
  }

  private static int thresholdFor(InstrumentType instrumentType) {
    return instrumentType == InstrumentType.ETF
        ? ETF_THRESHOLD_BUSINESS_DAYS
        : FUND_THRESHOLD_BUSINESS_DAYS;
  }

  private static LocalDate orderDate(TransactionOrder order) {
    return order.getOrderTimestamp().atZone(TALLINN).toLocalDate();
  }

  private static LocalDate addBusinessDays(LocalDate from, int businessDays) {
    LocalDate date = from;
    int count = 0;
    while (count < businessDays) {
      date = date.plusDays(1);
      if (isBusinessDay(date)) {
        count++;
      }
    }
    return date;
  }

  private static boolean isBusinessDay(LocalDate date) {
    DayOfWeek day = date.getDayOfWeek();
    return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;
  }

  private String buildMessage(
      LocalDate today,
      boolean fresh,
      List<OverdueLine> overdue,
      List<SebPendingTransactionRow> unmatched) {
    Map<TulevaFund, List<OverdueLine>> overdueByFund =
        overdue.stream().collect(Collectors.groupingBy(line -> line.order().getFund()));
    Map<TulevaFund, List<SebPendingTransactionRow>> unmatchedByFund =
        unmatched.stream()
            .filter(row -> fundResolver.resolve(row.clientName()).isPresent())
            .collect(
                Collectors.groupingBy(row -> fundResolver.resolve(row.clientName()).orElseThrow()));
    List<SebPendingTransactionRow> unresolved =
        unmatched.stream().filter(row -> fundResolver.resolve(row.clientName()).isEmpty()).toList();

    StringBuilder message = new StringBuilder();
    message.append("⚠️ SEB arveldus- ja tehingukontroll – ").append(today);
    if (!fresh) {
      message.append(
          "\n\nHOIATUS: värske SEB ootel-tehingute raport puudub või on aegunud —"
              + " arveldatud staatust ei saa tuvastada, kõik hilinenud täidetud tellimused"
              + " on allpool loetletud.");
    }

    for (TulevaFund fund : FUND_ORDER) {
      appendFundBlock(
          message,
          fund.getCode(),
          unmatchedByFund.getOrDefault(fund, List.of()),
          overdueByFund.getOrDefault(fund, List.of()));
    }
    appendFundBlock(message, "Tundmatu fond / lahendamata", unresolved, List.of());

    return message.toString();
  }

  private void appendFundBlock(
      StringBuilder message,
      String fundLabel,
      List<SebPendingTransactionRow> unmatched,
      List<OverdueLine> overdue) {
    if (unmatched.isEmpty() && overdue.isEmpty()) {
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
  }

  private static @Nullable LocalDate tradeDate(SebPendingTransactionRow row) {
    return row.tradeDate() == null ? null : row.tradeDate().atZone(TALLINN).toLocalDate();
  }

  private record OverdueLine(
      TransactionOrder order,
      ee.tuleva.onboarding.investment.transaction.OrderStatus status,
      LocalDate deadline) {}
}
