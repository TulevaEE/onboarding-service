package ee.tuleva.onboarding.savings;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

public interface RedemptionAlertThresholds {

  Optional<BigDecimal> redemptionPayoutWarningThreshold(TulevaFund fund, LocalDate asOf);

  Optional<BigDecimal> redemptionLiquidityWarningShareOfAum(TulevaFund fund, LocalDate asOf);
}
