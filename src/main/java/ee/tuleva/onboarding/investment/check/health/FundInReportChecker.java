package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.WARNING;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckType.FUND_IN_REPORT;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
class FundInReportChecker {

  List<HealthCheckFinding> fundsMissingFromReport(
      LocalDate navDate, Set<TulevaFund> fundsInReport) {
    return Arrays.stream(TulevaFund.values())
        .filter(TulevaFund::hasNavCalculation)
        .filter(fund -> !navDate.isBefore(fund.getInceptionDate()))
        .filter(fund -> !fundsInReport.contains(fund))
        .map(fund -> missing(fund, navDate))
        .toList();
  }

  private HealthCheckFinding missing(TulevaFund fund, LocalDate navDate) {
    return new HealthCheckFinding(
        fund,
        FUND_IN_REPORT,
        WARNING,
        ("%s: not in SEB's position report for navDate=%s. Ask SEB to resend the report with %s"
                + " before the %s NAV")
            .formatted(fund, navDate, fund, fund.getNavCutoffTime()));
  }
}
