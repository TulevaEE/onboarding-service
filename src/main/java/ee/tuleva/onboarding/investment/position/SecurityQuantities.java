package ee.tuleva.onboarding.investment.position;

import static ee.tuleva.onboarding.investment.position.AccountType.SECURITY;
import static java.util.stream.Collectors.toMap;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public final class SecurityQuantities {

  private SecurityQuantities() {}

  public static Map<String, BigDecimal> byIsin(List<FundPosition> positions) {
    return positions.stream()
        .filter(position -> position.getAccountType() == SECURITY)
        .filter(position -> position.getAccountId() != null && position.getQuantity() != null)
        .collect(toMap(FundPosition::getAccountId, FundPosition::getQuantity, BigDecimal::add));
  }

  public static boolean changedBetween(
      List<FundPosition> previousPositions, List<FundPosition> todayPositions) {
    var previous = byIsin(previousPositions);
    var today = byIsin(todayPositions);
    if (!previous.keySet().equals(today.keySet())) {
      return true;
    }
    return previous.entrySet().stream()
        .anyMatch(entry -> entry.getValue().compareTo(today.get(entry.getKey())) != 0);
  }
}
