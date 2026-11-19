package ee.tuleva.onboarding.kyb.screener;

import static ee.tuleva.onboarding.kyb.KybCheckType.ANNUAL_REPORTS;

import ee.tuleva.onboarding.kyb.KybCheck;
import ee.tuleva.onboarding.kyb.KybCompanyData;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AnnualReportsScreener implements KybScreener {

  @Override
  public List<KybCheck> screen(KybCompanyData companyData) {
    var history = companyData.registerHistory();
    return List.of(
        new KybCheck(
            ANNUAL_REPORTS,
            true,
            AnnualReportsMetadata.of(history.financialYears(), history.annualReports())));
  }
}
