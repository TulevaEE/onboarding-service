package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.ASSET;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.LIABILITY;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.EUR;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.FUND_UNIT;

import ee.tuleva.onboarding.ledger.LedgerAccount.AccountType;
import ee.tuleva.onboarding.ledger.LedgerAccount.AssetType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SystemAccount {
  INCOMING_PAYMENTS_CLEARING(ASSET, EUR),
  UNRECONCILED_BANK_RECEIPTS(ASSET, EUR),
  FUND_INVESTMENT_CASH_CLEARING(ASSET, EUR),
  FUND_UNITS_OUTSTANDING(LIABILITY, FUND_UNIT),
  PAYOUTS_CASH_CLEARING(ASSET, EUR);

  private final AccountType accountType;
  private final AssetType assetType;
}
