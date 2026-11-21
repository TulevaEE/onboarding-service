package ee.tuleva.onboarding.investment.transaction;

import static java.math.BigDecimal.ZERO;
import static java.util.Comparator.comparing;
import static java.util.Comparator.naturalOrder;
import static java.util.Comparator.nullsLast;
import static java.util.stream.Collectors.toUnmodifiableSet;

import ee.tuleva.onboarding.investment.transaction.SettledInTheBalance.AgainstTheReportRows;
import ee.tuleva.onboarding.investment.transaction.SettledInTheBalance.CarriedByAPendingFill;
import ee.tuleva.onboarding.investment.transaction.SettledInTheBalance.Unmatched;
import ee.tuleva.onboarding.ledger.TradeSettlementBooking;
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
            .filter(AgainstTheReportRows.class::isInstance)
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

  List<UnmatchedSettlement> unmatched() {
    return settlements.stream()
        .filter(Unmatched.class::isInstance)
        .map(SettledInTheBalance::booking)
        .sorted(
            comparing(TradeSettlementBooking::bookingDate)
                .thenComparing(TradeSettlementBooking::brokerReference, nullsLast(naturalOrder())))
        .map(UnmatchedSettlement::new)
        .toList();
  }

  BigDecimal unmatchedInflows() {
    return unmatched().stream()
        .map(UnmatchedSettlement::excludedFromTheBalance)
        .reduce(ZERO, BigDecimal::add);
  }

  Set<Long> carriedByPendingFills() {
    return settlements.stream()
        .filter(CarriedByAPendingFill.class::isInstance)
        .map(settled -> ((CarriedByAPendingFill) settled).executionId())
        .collect(toUnmodifiableSet());
  }

  private static BigDecimal sumOf(List<BigDecimal> amounts, Predicate<BigDecimal> side) {
    return amounts.stream().filter(side).reduce(ZERO, BigDecimal::add);
  }
}
