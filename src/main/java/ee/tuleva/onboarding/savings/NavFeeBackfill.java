package ee.tuleva.onboarding.savings;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.LocalDate;

public interface NavFeeBackfill {

  void backfillFees(TulevaFund fund, LocalDate from, LocalDate to);
}
