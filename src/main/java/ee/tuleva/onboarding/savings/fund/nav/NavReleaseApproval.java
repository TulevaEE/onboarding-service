package ee.tuleva.onboarding.savings.fund.nav;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record NavReleaseApproval(
    UUID calculationId, BigDecimal navPerUnit, BigDecimal aum, List<String> approvedBy) {

  public NavReleaseApproval {
    approvedBy = List.copyOf(approvedBy);
    if (approvedBy.isEmpty() || approvedBy.stream().anyMatch(String::isBlank)) {
      throw new IllegalArgumentException(
          "A NAV release needs its approvers: calculationId=" + calculationId);
    }
  }
}
