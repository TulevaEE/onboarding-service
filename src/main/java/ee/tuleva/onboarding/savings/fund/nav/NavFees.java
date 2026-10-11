package ee.tuleva.onboarding.savings.fund.nav;

import ee.tuleva.onboarding.comparisons.fundvalue.ResolvedPrice;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.LocalDate;
import java.util.Map;

public interface NavFees {

  NavFeeResult calculateFeesForNav(
      TulevaFund fund,
      LocalDate positionReportDate,
      NavFeeBases bases,
      Map<String, ResolvedPrice> securityPrices);
}
