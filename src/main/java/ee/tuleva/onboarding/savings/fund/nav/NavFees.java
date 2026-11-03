package ee.tuleva.onboarding.savings.fund.nav;

import ee.tuleva.onboarding.comparisons.fundvalue.ResolvedPrice;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

public interface NavFees {

  NavFeeResult calculateFeesForNav(
      TulevaFund fund,
      LocalDate positionReportDate,
      BigDecimal feeBaseValue,
      Map<String, ResolvedPrice> securityPrices);
}
