package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.investment.position.AccountType.CASH;
import static ee.tuleva.onboarding.investment.position.AccountType.LIABILITY;
import static ee.tuleva.onboarding.investment.position.AccountType.NAV;
import static ee.tuleva.onboarding.investment.position.AccountType.RECEIVABLES;
import static ee.tuleva.onboarding.investment.position.AccountType.SECURITY;
import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.investment.position.AccountType;
import ee.tuleva.onboarding.investment.position.FundPosition;
import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

final class PositionTotals {

  private static final EnumSet<AccountType> NET_ASSET_TYPES =
      EnumSet.of(SECURITY, CASH, RECEIVABLES, LIABILITY);

  private PositionTotals() {}

  static BigDecimal aum(List<FundPosition> positions) {
    var navRows = positions.stream().filter(position -> position.getAccountType() == NAV).toList();
    if (!navRows.isEmpty() && navRows.getFirst().getMarketValue() != null) {
      return navRows.getFirst().getMarketValue();
    }
    return total(positions, position -> NET_ASSET_TYPES.contains(position.getAccountType()));
  }

  static BigDecimal total(List<FundPosition> positions, Predicate<FundPosition> filter) {
    return positions.stream()
        .filter(filter)
        .map(FundPosition::getMarketValue)
        .filter(Objects::nonNull)
        .reduce(ZERO, BigDecimal::add);
  }
}
