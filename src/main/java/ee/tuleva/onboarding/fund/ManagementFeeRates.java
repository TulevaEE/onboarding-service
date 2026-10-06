package ee.tuleva.onboarding.fund;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

public interface ManagementFeeRates {

  Optional<BigDecimal> rateInForceOn(TulevaFund fund, LocalDate date);
}
