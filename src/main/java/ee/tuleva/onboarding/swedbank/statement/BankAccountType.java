package ee.tuleva.onboarding.swedbank.statement;

import static ee.tuleva.onboarding.ledger.SystemAccount.*;

import ee.tuleva.onboarding.ledger.SystemAccount;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum BankAccountType {
  DEPOSIT_EUR(INCOMING_PAYMENTS_CLEARING),
  WITHDRAWAL_EUR(PAYOUTS_CASH_CLEARING),
  FUND_INVESTMENT_EUR(FUND_INVESTMENT_CASH_CLEARING);

  private final SystemAccount ledgerAccount;
}
