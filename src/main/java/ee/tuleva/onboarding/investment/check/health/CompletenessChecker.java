package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.WARNING;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckType.COMPLETENESS;
import static ee.tuleva.onboarding.investment.position.AccountType.CASH;
import static ee.tuleva.onboarding.investment.position.AccountType.SECURITY;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.position.FundPosition;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
class CompletenessChecker {

  List<HealthCheckFinding> check(TulevaFund fund, LocalDate navDate, List<FundPosition> positions) {
    var findings = new ArrayList<HealthCheckFinding>();

    boolean hasSecurities = positions.stream().anyMatch(p -> p.getAccountType() == SECURITY);
    boolean hasCash = positions.stream().anyMatch(p -> p.getAccountType() == CASH);

    if (!hasSecurities) {
      findings.add(
          new HealthCheckFinding(
              fund,
              COMPLETENESS,
              WARNING,
              "%s: no SECURITY positions found for navDate=%s".formatted(fund, navDate)));
    }
    if (!hasCash) {
      findings.add(
          new HealthCheckFinding(
              fund,
              COMPLETENESS,
              WARNING,
              "%s: no CASH position found for navDate=%s".formatted(fund, navDate)));
    }

    return findings;
  }
}
