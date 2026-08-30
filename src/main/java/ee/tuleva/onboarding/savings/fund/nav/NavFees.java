package ee.tuleva.onboarding.savings.fund.nav;

import ee.tuleva.onboarding.comparisons.fundvalue.ResolvedPrice;
import ee.tuleva.onboarding.fund.TulevaFund;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

public interface NavFees {

  NavFeeResult calculateFeesForNav(
      TulevaFund fund,
      LocalDate positionReportDate,
      NavFeeBases bases,
      Instant feeCutoff,
      Map<String, ResolvedPrice> securityPrices);

  boolean chargedToFund(TulevaFund fund, NavFeeType feeType, LocalDate date);
}
