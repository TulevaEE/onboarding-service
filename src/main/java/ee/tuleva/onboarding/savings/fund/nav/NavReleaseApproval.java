package ee.tuleva.onboarding.savings.fund.nav;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record NavReleaseApproval(
    UUID calculationId, BigDecimal navPerUnit, BigDecimal aum, List<String> approvedBy) {

  private static final int TWO_PEOPLE_APPROVE_A_NAV_RELEASE = 2;

  public NavReleaseApproval(
      @Nullable UUID calculationId,
      @Nullable BigDecimal navPerUnit,
      @Nullable BigDecimal aum,
      @Nullable List<String> approvedBy) {
    if (calculationId == null || navPerUnit == null || aum == null || approvedBy == null) {
      throw new IllegalArgumentException(
          "A NAV release needs calculationId, navPerUnit, aum and approvedBy");
    }
    if (approvedBy.stream().anyMatch(String::isBlank)
        || approvedBy.stream().distinct().count() < TWO_PEOPLE_APPROVE_A_NAV_RELEASE) {
      throw new IllegalArgumentException(
          "A NAV release needs two different approvers: calculationId=" + calculationId);
    }
    this.calculationId = calculationId;
    this.navPerUnit = navPerUnit;
    this.aum = aum;
    this.approvedBy = List.copyOf(approvedBy);
  }
}
