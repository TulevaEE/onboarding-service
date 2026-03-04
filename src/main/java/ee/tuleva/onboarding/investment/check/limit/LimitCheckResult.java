package ee.tuleva.onboarding.investment.check.limit;

import static ee.tuleva.onboarding.investment.check.limit.BreachSeverity.OK;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record LimitCheckResult(
    TulevaFund fund,
    LocalDate checkDate,
    List<PositionBreach> positionBreaches,
    List<ProviderBreach> providerBreaches,
    @Nullable ReserveBreach reserveBreach,
    @Nullable FreeCashBreach freeCashBreach) {

  public boolean hasBreaches() {
    return positionBreaches.stream().anyMatch(b -> b.severity() != OK)
        || providerBreaches.stream().anyMatch(b -> b.severity() != OK)
        || (reserveBreach != null && reserveBreach.severity() != OK)
        || (freeCashBreach != null && freeCashBreach.severity() != OK);
  }
}
