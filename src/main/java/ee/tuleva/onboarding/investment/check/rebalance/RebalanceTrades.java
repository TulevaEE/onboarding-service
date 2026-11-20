package ee.tuleva.onboarding.investment.check.rebalance;

import static ee.tuleva.onboarding.investment.JobRunSchedule.TIMEZONE;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.EXECUTED;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.SENT;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.SETTLED;
import static java.util.stream.Collectors.toUnmodifiableSet;

import ee.tuleva.onboarding.investment.transaction.OrderStatus;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import ee.tuleva.onboarding.investment.transaction.TransactionOrderRepository;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class RebalanceTrades {

  private static final Set<OrderStatus> PLACED_ORDERS = EnumSet.of(SENT, EXECUTED, SETTLED);
  private static final ZoneId TALLINN = ZoneId.of(TIMEZONE);

  private final TransactionOrderRepository orderRepository;

  Set<String> tradedIsins(TulevaFund fund, ModelChange change, LocalDate measuredUntil) {
    return orderRepository
        .findPlacedBetween(
            fund,
            PLACED_ORDERS,
            change.effectiveDate().atStartOfDay(TALLINN).toInstant(),
            measuredUntil.plusDays(1).atStartOfDay(TALLINN).toInstant())
        .stream()
        .filter(RebalanceTrades::knownToHaveGoneOut)
        .map(TransactionOrder::getInstrumentIsin)
        .filter(change.changedIsins()::contains)
        .collect(toUnmodifiableSet());
  }

  private static boolean knownToHaveGoneOut(TransactionOrder order) {
    return order.getOrderStatus() != SENT || order.getBatch().importedFromTheTradeRegistry();
  }
}
