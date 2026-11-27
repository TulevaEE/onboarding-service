package ee.tuleva.onboarding.savings.fund.nav;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record NavReleaseStatus(
    String fundCode,
    LocalDate navDate,
    UUID calculationId,
    NavReleaseState state,
    BigDecimal navPerUnit,
    BigDecimal aum,
    @Nullable BigDecimal sentNavPerUnit) {

  boolean hasTheApprovedValues(NavReleaseApproval approval) {
    return navPerUnit.compareTo(approval.navPerUnit()) == 0 && aum.compareTo(approval.aum()) == 0;
  }
}
