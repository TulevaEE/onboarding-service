package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.WARNING;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckType.OUTSTANDING_UNITS;
import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.position.FundPosition;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
class OutstandingUnitsChecker {

  List<HealthCheckFinding> check(
      TulevaFund fund, LocalDate navDate, List<FundPosition> unitsPositions) {
    if (unitsPositions.isEmpty()) {
      return List.of(
          new HealthCheckFinding(
              fund,
              OUTSTANDING_UNITS,
              WARNING,
              "%s: no UNITS position found for navDate=%s".formatted(fund, navDate)));
    }

    var quantity = unitsPositions.getFirst().getQuantity();
    if (quantity == null || quantity.compareTo(ZERO) == 0) {
      return List.of(
          new HealthCheckFinding(
              fund,
              OUTSTANDING_UNITS,
              WARNING,
              "%s: UNITS quantity is %s for navDate=%s".formatted(fund, quantity, navDate)));
    }

    return List.of();
  }
}
