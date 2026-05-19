package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.JobRunSchedule.TIMEZONE;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.EXECUTED;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.SENT;

import ee.tuleva.onboarding.investment.transaction.InstrumentType;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import ee.tuleva.onboarding.investment.transaction.TransactionOrderRepository;
import ee.tuleva.onboarding.notification.email.EmailService;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile({"production", "staging"})
@RequiredArgsConstructor
class OverdueSettlementJob {

  private static final int ETF_THRESHOLD_BUSINESS_DAYS = 3;
  private static final int FUND_THRESHOLD_BUSINESS_DAYS = 5;
  private static final ZoneId TALLINN = ZoneId.of(TIMEZONE);

  private final Clock clock;
  private final TransactionOrderRepository orderRepository;
  private final AlertMandrillMessageFactory messageFactory;
  private final EmailService emailService;

  @Scheduled(cron = "0 0 10 * * *", zone = TIMEZONE)
  @SchedulerLock(name = "OverdueSettlementJob", lockAtMostFor = "10m", lockAtLeastFor = "1m")
  public void run() {
    LocalDate today = LocalDate.now(clock);
    List<TransactionOrder> active = orderRepository.findOverdueOrders(SENT, EXECUTED);

    List<TransactionOrder> overdue =
        active.stream().filter(order -> isOverdue(order, today)).toList();

    if (overdue.isEmpty()) {
      log.info("No overdue settlements found: today={}, scannedCount={}", today, active.size());
      return;
    }

    var subject = "HOIATUS: " + overdue.size() + " hilinenud tehing(ut) ootel";
    var body = buildBody(overdue, today);
    boolean sent = emailService.sendSystemEmail(messageFactory.create(subject, body));
    if (sent) {
      log.info(
          "Sent overdue settlement alert: today={}, overdueCount={}, scannedCount={}",
          today,
          overdue.size(),
          active.size());
    } else {
      log.error(
          "Failed to send overdue settlement alert: today={}, overdueCount={}",
          today,
          overdue.size());
    }
  }

  // Deadline = orderTimestamp + N business days (N=3 ETF, N=5 FUND). Mirrors the AppScript at
  // tmp/transaction-registry/Tehingute register 2026 (SEB) - Import_Orders -
  // transaction_registry.gs:1994, which alerts when latestSebReportDate > orderTimestamp + N bdays.
  // Strict ">" so the first alert fires on business day N+1.
  private static boolean isOverdue(TransactionOrder order, LocalDate today) {
    if (order.getOrderTimestamp() == null || order.getInstrumentType() == null) {
      return false;
    }
    int threshold =
        order.getInstrumentType() == InstrumentType.ETF
            ? ETF_THRESHOLD_BUSINESS_DAYS
            : FUND_THRESHOLD_BUSINESS_DAYS;
    LocalDate orderDate = order.getOrderTimestamp().atZone(TALLINN).toLocalDate();
    LocalDate deadline = addBusinessDays(orderDate, threshold);
    return deadline.isBefore(today);
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

  private static String buildBody(List<TransactionOrder> overdue, LocalDate today) {
    String list =
        overdue.stream()
            .map(OverdueSettlementJob::formatOrderLine)
            .collect(Collectors.joining("\n\n"));

    return """
        Tänase seisuga (%s) on ootel %d tehingut, mille arveldus on tähtaja ületanud.

        ETF tehingutel on lubatud kuni 3 tööpäeva pärast tellimuse saatmist SEB-i.
        FOND tehingutel on lubatud kuni 5 tööpäeva pärast tellimuse saatmist SEB-i.

        %s
        """
        .formatted(today, overdue.size(), list);
  }

  private static String formatOrderLine(TransactionOrder order) {
    return """
        Order id: %d
        Fund: %s
        ISIN: %s
        Instrument type: %s
        Order status: %s
        Order venue: %s
        Order sent: %s
        Expected settlement: %s
        Order uuid: %s"""
        .formatted(
            order.getId(),
            order.getFund(),
            order.getInstrumentIsin(),
            order.getInstrumentType(),
            order.getOrderStatus(),
            order.getOrderVenue(),
            order.getOrderTimestamp(),
            order.getExpectedSettlementDate(),
            order.getOrderUuid());
  }
}
