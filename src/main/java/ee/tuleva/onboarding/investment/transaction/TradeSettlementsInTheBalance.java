package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.transaction.SettledInTheBalance.Carrier.PENDING_ORDER;
import static java.util.stream.Collectors.toUnmodifiableSet;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.NullMarked;

@NullMarked
record TradeSettlementsInTheBalance(List<SettledInTheBalance> settlements) {

  Set<String> carriedByPendingOrders() {
    return settlements.stream()
        .filter(settled -> settled.carrier() == PENDING_ORDER)
        .map(settled -> settled.booking().brokerReference())
        .filter(Objects::nonNull)
        .collect(toUnmodifiableSet());
  }
}
