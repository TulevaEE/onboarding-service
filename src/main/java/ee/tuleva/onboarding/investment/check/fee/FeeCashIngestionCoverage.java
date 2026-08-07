package ee.tuleva.onboarding.investment.check.fee;

import ee.tuleva.onboarding.fund.TulevaFund;
import org.springframework.stereotype.Component;

// The single place that decides which funds the cash leg can speak about. Bank statement ingestion
// only exists for the savings fund today - BankAccountType covers its three accounts and
// SavingsFundLedger resolves every system account against TKF100. When the SEB gateway expands to
// the pension funds, this predicate is the only thing in this package that changes.
@Component
class FeeCashIngestionCoverage {

  boolean coversFund(TulevaFund fund) {
    return fund.isSavingsFund();
  }
}
