package ee.tuleva.onboarding.investment.transaction;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.LocalDate;
import java.util.List;

class TradePrepInputGateException extends RuntimeException {

  TradePrepInputGateException(
      TulevaFund fund, LocalDate asOfDate, List<InputGateFailure> failures) {
    super(
        "Trade prep inputs missing or stale, no DRAFT made: fund=%s, asOfDate=%s, failures=%s"
            .formatted(fund, asOfDate, failures));
  }
}
