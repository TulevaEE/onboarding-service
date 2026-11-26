package ee.tuleva.onboarding.savings;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;

public interface IssueRedemptionPriceThreshold {

  BigDecimal issueRedemptionPriceDeviationThreshold(TulevaFund fund, LocalDate asOf);
}
