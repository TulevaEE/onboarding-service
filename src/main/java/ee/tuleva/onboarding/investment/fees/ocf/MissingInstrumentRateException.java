package ee.tuleva.onboarding.investment.fees.ocf;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.LocalDate;
import java.util.List;

class MissingInstrumentRateException extends IllegalStateException {

  MissingInstrumentRateException(TulevaFund fund, LocalDate asOf, List<String> isins) {
    super(
        "Missing underlying fund OCF rate: fund="
            + fund.getCode()
            + ", asOf="
            + asOf
            + ", isins="
            + String.join(",", isins));
  }
}
