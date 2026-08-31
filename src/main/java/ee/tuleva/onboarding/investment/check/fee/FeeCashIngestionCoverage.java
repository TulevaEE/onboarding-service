package ee.tuleva.onboarding.investment.check.fee;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import org.springframework.stereotype.Component;

// Bank statement ingestion only exists for the savings fund today: BankAccountType covers its three
// accounts and SavingsFundLedger resolves every system account against TKF100.
@Component
class FeeCashIngestionCoverage {

  boolean coversFund(TulevaFund fund) {
    return fund.isSavingsFund();
  }
}
