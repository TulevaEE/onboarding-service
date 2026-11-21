package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.transaction.SettledInTheBalance.Carrier.PENDING_ORDER;
import static ee.tuleva.onboarding.investment.transaction.SettledInTheBalance.Carrier.REPORT_ROWS;
import static java.math.BigDecimal.ZERO;
import static java.util.stream.Collectors.toUnmodifiableSet;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import org.jspecify.annotations.NullMarked;

@NullMarked
record TradeSettlementsInTheBalance(List<SettledInTheBalance> settlements) {

  Netted<UnsettledTrades> net(UnsettledTrades atReportDate) {
    List<BigDecimal> againstTheReportRows =
        settlements.stream()
            .filter(settled -> settled.carrier() == REPORT_ROWS)
            .map(settled -> Objects.requireNonNull(settled.booking().cashAmount()))
            .toList();
    BigDecimal paid = sumOf(againstTheReportRows, amount -> amount.signum() < 0).negate();
    BigDecimal received = sumOf(againstTheReportRows, amount -> amount.signum() > 0);
    return new Netted<>(
        atReportDate,
        new UnsettledTrades(
            ZERO.max(atReportDate.payables().subtract(paid)),
            ZERO.max(atReportDate.receivables().subtract(received))));
  }

  private static BigDecimal sumOf(List<BigDecimal> amounts, Predicate<BigDecimal> side) {
    return amounts.stream().filter(side).reduce(ZERO, BigDecimal::add);
  }

  Set<String> carriedByPendingOrders() {
    return settlements.stream()
        .filter(settled -> settled.carrier() == PENDING_ORDER)
        .map(settled -> settled.booking().brokerReference())
        .filter(Objects::nonNull)
        .collect(toUnmodifiableSet());
  }
}
