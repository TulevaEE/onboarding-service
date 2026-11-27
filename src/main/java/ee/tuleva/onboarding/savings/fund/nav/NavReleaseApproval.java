package ee.tuleva.onboarding.savings.fund.nav;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

public record NavReleaseApproval(BigDecimal navPerUnit, BigDecimal aum, List<String> approvedBy) {

  private static final int TWO_PEOPLE_APPROVE_A_NAV_RELEASE = 2;

  public NavReleaseApproval(
      @Nullable BigDecimal navPerUnit,
      @Nullable BigDecimal aum,
      @Nullable List<String> approvedBy) {
    if (navPerUnit == null || aum == null || approvedBy == null) {
      throw new IllegalArgumentException("A NAV release needs navPerUnit, aum and approvedBy");
    }
    if (approvedBy.stream().anyMatch(String::isBlank)
        || differentPeopleAmong(approvedBy) < TWO_PEOPLE_APPROVE_A_NAV_RELEASE) {
      throw new IllegalArgumentException(
          "A NAV release needs two different approvers: navPerUnit=" + navPerUnit);
    }
    this.navPerUnit = navPerUnit;
    this.aum = aum;
    this.approvedBy = List.copyOf(approvedBy);
  }

  public int numberOfApprovers() {
    return approvedBy.size();
  }

  private static long differentPeopleAmong(List<String> approvers) {
    return approvers.stream().map(NavReleaseApproval::asOnePerson).distinct().count();
  }

  private static String asOnePerson(String approver) {
    return approver.strip().toLowerCase(Locale.ROOT);
  }
}
