package ee.tuleva.onboarding.investment.check.rebalance;

import static ee.tuleva.onboarding.investment.JobRunSchedule.TIMEZONE;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.EXECUTED;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.SENT;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.SETTLED;
import static ee.tuleva.onboarding.investment.transaction.TransactionMode.REBALANCE;

import ee.tuleva.onboarding.investment.check.rebalance.RebalanceOrdersAwaitingExecution.SentOrder;
import ee.tuleva.onboarding.investment.transaction.OrderStatus;
import ee.tuleva.onboarding.investment.transaction.TransactionOrderRepository;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
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

  Optional<LocalDate> firstRebalancingTradeDate(
      TulevaFund fund, LocalDate modelEffectiveDate, LocalDate measuredUntil) {
    return orderRepository
        .findFirstTradeTimestampOfATradePrepRun(
            fund,
            REBALANCE,
            EXECUTED_BY_THE_BROKER,
            modelEffectiveDate.atStartOfDay(TALLINN).toInstant(),
            measuredUntil.plusDays(1).atStartOfDay(TALLINN).toInstant())
        .map(tradeTimestamp -> tradeTimestamp.atZone(TALLINN).toLocalDate());
  }

  RebalanceOrdersAwaitingExecution rebalanceOrdersAwaitingExecution(
      TulevaFund fund, LocalDate modelEffectiveDate, LocalDate measuredUntil) {
    return new RebalanceOrdersAwaitingExecution(
        orderRepository
            .findOrdersOfATradePrepRun(
                fund,
                REBALANCE,
                SENT,
                modelEffectiveDate.atStartOfDay(TALLINN).toInstant(),
                measuredUntil.plusDays(1).atStartOfDay(TALLINN).toInstant())
            .stream()
            .map(
                order ->
                    new SentOrder(
                        order.orderTimestamp().atZone(TALLINN).toLocalDate(),
                        order.expectedSettlementDate()))
            .toList());
  }
}
