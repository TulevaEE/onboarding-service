package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.ASSET;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.LIABILITY;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.EUR;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.FUND_UNIT;
import static ee.tuleva.onboarding.ledger.SystemAccount.Category.*;

import ee.tuleva.onboarding.ledger.LedgerAccount.AccountType;
import ee.tuleva.onboarding.ledger.LedgerAccount.AssetType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SystemAccount {
  // Payment processing
  INCOMING_PAYMENTS_CLEARING(
      ASSET, EUR, PAYMENT_PROCESSING, "Incoming payments before user attribution"),
  PAYOUTS_CASH_CLEARING(ASSET, EUR, PAYMENT_PROCESSING, "Cash reserved for redemption payouts"),
  BANK_FEE_ACCRUAL(LIABILITY, EUR, PAYMENT_PROCESSING, "Accrued bank transaction fees"),

  // Bank reconciliation
  UNRECONCILED_BANK_RECEIPTS(ASSET, EUR, BANK_RECONCILIATION, "Bank receipts not yet matched"),

  // Fund operations
  FUND_INVESTMENT_CASH_CLEARING(ASSET, EUR, FUND_OPERATIONS, "Cash reserved for unit purchases"),
  FUND_UNITS_OUTSTANDING(LIABILITY, FUND_UNIT, FUND_OPERATIONS, "Total units issued to investors"),

  // NAV calculation - Position data (from SEB)
  SECURITIES_VALUE(ASSET, EUR, NAV_CALCULATION, "Securities market value from custodian"),
  CASH_POSITION(ASSET, EUR, NAV_CALCULATION, "Cash balance from custodian"),
  TRADE_RECEIVABLES(ASSET, EUR, NAV_CALCULATION, "Trade settlement receivables"),
  TRADE_PAYABLES(LIABILITY, EUR, NAV_CALCULATION, "Trade settlement payables"),

  // NAV calculation - Fee accruals
  MANAGEMENT_FEE_ACCRUAL(LIABILITY, EUR, NAV_CALCULATION, "Accrued management fees"),
  DEPOT_FEE_ACCRUAL(LIABILITY, EUR, NAV_CALCULATION, "Accrued depot fees"),

  // NAV calculation - Equity (balancing account for positions)
  NAV_EQUITY(LIABILITY, EUR, NAV_CALCULATION, "Equity account balancing NAV positions"),

  // NAV calculation - Manual adjustments
  BLACKROCK_ADJUSTMENT(ASSET, EUR, NAV_CALCULATION, "Partner rebates/fees adjustment");

  private final AccountType accountType;
  private final AssetType assetType;
  private final Category category;
  private final String description;

  public enum Category {
    PAYMENT_PROCESSING,
    BANK_RECONCILIATION,
    FUND_OPERATIONS,
    NAV_CALCULATION
  }
}
