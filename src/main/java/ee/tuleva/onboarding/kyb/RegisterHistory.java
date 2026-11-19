package ee.tuleva.onboarding.kyb;

import ee.tuleva.onboarding.ariregister.AnnualReport;
import ee.tuleva.onboarding.ariregister.FinancialYear;
import java.util.List;

public record RegisterHistory(
    List<KybOwnership> ownerships,
    List<FinancialYear> financialYears,
    List<AnnualReport> annualReports) {

  public RegisterHistory {
    ownerships = List.copyOf(ownerships);
    financialYears = List.copyOf(financialYears);
    annualReports = List.copyOf(annualReports);
  }
}
