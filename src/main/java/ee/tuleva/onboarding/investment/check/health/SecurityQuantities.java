package ee.tuleva.onboarding.investment.check.health;

import ee.tuleva.onboarding.investment.position.FundPosition;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

final class SecurityQuantities {

  private SecurityQuantities() {}

  // fund_position is unique on account name, not ISIN, so one instrument may legally arrive on
  // several rows — compare aggregates, never row by row.
  static Map<String, BigDecimal> byIsin(List<FundPosition> positions) {
    return positions.stream()
        .filter(position -> position.getAccountId() != null && position.getQuantity() != null)
        .collect(
            Collectors.toMap(
                FundPosition::getAccountId, FundPosition::getQuantity, BigDecimal::add));
  }
}
