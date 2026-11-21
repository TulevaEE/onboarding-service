package ee.tuleva.onboarding.investment.check.rebalance;

import static ee.tuleva.onboarding.investment.JobRunSchedule.TIMEZONE;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.EXECUTED;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.SENT;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.SETTLED;
import static ee.tuleva.onboarding.investment.transaction.TransactionMode.REBALANCE;
import static java.util.Comparator.naturalOrder;

import ee.tuleva.onboarding.investment.calendar.EstonianCalendar;
import ee.tuleva.onboarding.investment.transaction.OrderStatus;
import ee.tuleva.onboarding.investment.transaction.TransactionOrderRepository;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class RebalanceTrades {

  private static final Set<OrderStatus> EXECUTED_BY_THE_BROKER = EnumSet.of(EXECUTED, SETTLED);
  private static final ZoneId TALLINN = ZoneId.of(TIMEZONE);

  private final TransactionOrderRepository orderRepository;
  private final EstonianCalendar estonianCalendar;

  Optional<LocalDate> firstRebalancingTradeDate(
      TulevaFund fund, LocalDate modelEffectiveDate, LocalDate measuredUntil) {
    return orderRepository
        .findExecutionsOfATradePrepRun(
            fund,
            REBALANCE,
            EXECUTED_BY_THE_BROKER,
            modelEffectiveDate.atStartOfDay(TALLINN).toInstant(),
            measuredUntil.plusDays(1).atStartOfDay(TALLINN).toInstant())
        .stream()
        .flatMap(
            execution -> execution.tradeDateOrTheDayTheCustodianFirstReportedIt(TALLINN).stream())
        .filter(tradeDate -> !tradeDate.isAfter(measuredUntil))
        .min(naturalOrder());
  }

  List<SentRebalanceOrder> sentRebalanceOrders(
      TulevaFund fund, LocalDate modelEffectiveDate, LocalDate measuredUntil) {
    return orderRepository
        .findOrdersOfATradePrepRun(
            fund,
            REBALANCE,
            SENT,
            modelEffectiveDate.atStartOfDay(TALLINN).toInstant(),
            measuredUntil.plusDays(1).atStartOfDay(TALLINN).toInstant())
        .stream()
        .map(
            order ->
                SentRebalanceOrder.of(
                    order.instrumentIsin(),
                    order.orderTimestamp().atZone(TALLINN).toLocalDate(),
                    order.expectedSettlementDate(),
                    estonianCalendar))
        .toList();
  }
}
