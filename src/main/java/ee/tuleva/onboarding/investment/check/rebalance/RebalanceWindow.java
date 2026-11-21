package ee.tuleva.onboarding.investment.check.rebalance;

import static ee.tuleva.onboarding.investment.check.rebalance.RebalanceState.AWAITING_EXECUTION;
import static ee.tuleva.onboarding.investment.check.rebalance.RebalanceState.FAIL;
import static ee.tuleva.onboarding.investment.check.rebalance.RebalanceState.INFO;
import static ee.tuleva.onboarding.investment.check.rebalance.RebalanceState.OK;
import static ee.tuleva.onboarding.investment.check.rebalance.RebalanceState.WARNING;
import static java.time.temporal.ChronoUnit.DAYS;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public record RebalanceWindow(
    TulevaFund fund,
    LocalDate modelEffectiveDate,
    LocalDate checkDate,
    BigDecimal tolerance,
    @Nullable LocalDate latestNavDate,
    List<Deviation> outsideToleranceAtLatestNav,
    @Nullable RebalanceClosure closure,
    List<SentRebalanceOrder> sentRebalanceOrders,
    LocalDate deadline,
    LocalDate warningFrom) {

  public RebalanceState state() {
    if (closed()) {
      return OK;
    }
    if (deadlinePassed()) {
      return anOrderSentByTheDeadlineStillAwaitsConfirmation() ? AWAITING_EXECUTION : FAIL;
    }
    if (!checkDate.isBefore(warningFrom)) {
      return WARNING;
    }
    return INFO;
  }

  public boolean closed() {
    return closure != null;
  }

  public Optional<LocalDate> closedOn() {
    return Optional.ofNullable(closure).map(RebalanceClosure::on);
  }

  private boolean deadlinePassed() {
    return checkDate.isAfter(deadline);
  }

  public List<SentRebalanceOrder> ordersAwaitingConfirmation() {
    return sentRebalanceOrders.stream()
        .filter(order -> !order.confirmationOverdueOn(checkDate))
        .toList();
  }

  public List<SentRebalanceOrder> ordersOverdueForConfirmation() {
    return sentRebalanceOrders.stream()
        .filter(order -> order.confirmationOverdueOn(checkDate))
        .toList();
  }

  private boolean anOrderSentByTheDeadlineStillAwaitsConfirmation() {
    return ordersAwaitingConfirmation().stream()
        .map(SentRebalanceOrder::sentOn)
        .anyMatch(sentOn -> !sentOn.isAfter(deadline));
  }

  public long daysUntilDeadline() {
    return DAYS.between(checkDate, deadline);
  }
}
