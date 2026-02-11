# NAV Calculation: Ledger as Source of Truth

## Goal

Refactor NAV calculation so ALL inputs come from the ledger. External data (SEB positions, fee accruals) must be recorded to ledger first, then NAV calculation reads only from ledger.

## Current State vs Target State

### Current (Mixed Sources)
```
NAV Calculation reads from:
├── Ledger (some inputs)
│   ├── CASH_RESERVED (subscriptions)
│   ├── FUND_UNITS_RESERVED (redemptions)
│   ├── FUND_UNITS_OUTSTANDING (units)
│   └── BLACKROCK_ADJUSTMENT (manual)
│
└── External Sources (other inputs)
    ├── FundPositionRepository (securities, cash, receivables, payables)
    └── Calculated on-the-fly (fee accruals)
```

### Target (Ledger Only)
```
1. Data Import Jobs record to Ledger
   ├── SEB Position Import → Ledger accounts
   └── Fee Accrual Job → Ledger accounts

2. NAV Calculation reads ONLY from Ledger
   └── All components query ledger balances
```

## Benefits

1. **Single Source of Truth** - All NAV inputs in one place
2. **Auditability** - Complete record of what data was used
3. **Reproducibility** - Can recalculate any historical NAV
4. **Consistency** - Data can't change between reads
5. **Reconciliation** - Clear point to reconcile against external reports

## New System Accounts

Add to `SystemAccount.java` under NAV_CALCULATION category:

```java
// NAV calculation - Position data (from SEB)
SECURITIES_VALUE(ASSET, EUR, NAV_CALCULATION, "Securities market value from custodian"),
CASH_POSITION(ASSET, EUR, NAV_CALCULATION, "Cash balance from custodian"),
TRADE_RECEIVABLES(ASSET, EUR, NAV_CALCULATION, "Trade settlement receivables"),
TRADE_PAYABLES(LIABILITY, EUR, NAV_CALCULATION, "Trade settlement payables"),

// NAV calculation - Fee accruals
MANAGEMENT_FEE_ACCRUAL(LIABILITY, EUR, NAV_CALCULATION, "Accrued management fees"),
CUSTODY_FEE_ACCRUAL(LIABILITY, EUR, NAV_CALCULATION, "Accrued custody fees"),

// NAV calculation - Equity (balancing account for positions)
NAV_EQUITY(LIABILITY, EUR, NAV_CALCULATION, "Equity account balancing NAV positions"),

// Already exists
BLACKROCK_ADJUSTMENT(ASSET, EUR, NAV_CALCULATION, "Partner rebates/fees adjustment"),
```

**Total: 7 new accounts** (SECURITIES_VALUE, CASH_POSITION, TRADE_RECEIVABLES, TRADE_PAYABLES, MANAGEMENT_FEE_ACCRUAL, CUSTODY_FEE_ACCRUAL, NAV_EQUITY)

## Implementation Phases

### Phase 1: Add New System Accounts

**Files to modify:**
- `SystemAccount.java` - Add 6 new accounts

**Migration:**
- None needed (accounts created on first use)

### Phase 2: Position Data to Ledger

**Create:** `NavPositionLedger.java`

Service to record SEB position data to ledger:

```java
@Service
public class NavPositionLedger {

  // Called after SEB position import
  public void recordPositions(TulevaFund fund, LocalDate reportDate) {
    // Get position data from FundPositionRepository
    // Create balanced ledger transactions:

    // Securities: SECURITIES_VALUE ↔ NAV_EQUITY_ACCOUNT
    // Cash: CASH_POSITION ↔ NAV_EQUITY_ACCOUNT
    // Receivables: TRADE_RECEIVABLES ↔ NAV_EQUITY_ACCOUNT
    // Payables: NAV_EQUITY_ACCOUNT ↔ TRADE_PAYABLES

    // Store reportDate in transaction metadata
  }
}
```

**Modify:** `FundPositionImportJob.java`
- After importing positions, call `NavPositionLedger.recordPositions()`

### Phase 3: Fee Accruals to Ledger

**Create:** `NavFeeAccrualLedger.java`

Service to record fee accruals to ledger:

```java
@Service
public class NavFeeAccrualLedger {

  // Called after daily fee calculation
  public void recordFeeAccruals(TulevaFund fund, LocalDate date) {
    // Get fee accruals from FeeAccrualRepository
    // Create balanced ledger transactions:

    // Management fee: NAV_EXPENSE ↔ MANAGEMENT_FEE_ACCRUAL
    // Custody fee: NAV_EXPENSE ↔ CUSTODY_FEE_ACCRUAL
  }
}
```

**Modify:** `FeeCalculationScheduledJob.java`
- After calculating fees, call `NavFeeAccrualLedger.recordFeeAccruals()`

### Phase 4: Update NAV Components

Modify each component to read from ledger instead of external sources:

| Component | Current Source | New Source |
|-----------|---------------|------------|
| SecuritiesValueComponent | PositionCalculationService | SECURITIES_VALUE balance |
| CashPositionComponent | FundPositionRepository | CASH_POSITION balance |
| ReceivablesComponent | FundPositionRepository | TRADE_RECEIVABLES balance |
| PayablesComponent | FundPositionRepository | TRADE_PAYABLES balance |
| ManagementFeeAccrualComponent | Calculated | MANAGEMENT_FEE_ACCRUAL balance |
| CustodyFeeAccrualComponent | Calculated | CUSTODY_FEE_ACCRUAL balance |
| SubscriptionsComponent | Already ledger | No change |
| RedemptionsComponent | Already ledger | No change |
| BlackrockAdjustmentComponent | Already ledger | No change |

### Phase 5: Validation & Reconciliation

**Create:** `NavLedgerReconciliation.java`

Service to validate ledger matches external sources:

```java
@Service
public class NavLedgerReconciliation {

  public ReconciliationResult reconcile(TulevaFund fund, LocalDate date) {
    // Compare ledger balances vs FundPosition data
    // Compare ledger balances vs calculated fee accruals
    // Return discrepancies
  }
}
```

### Phase 6: Add Missing Validations

In `NavCalculationService.validateResult()`:

```java
private void validateResult(NavCalculationResult result) {
  // Existing: NAV per unit positive
  if (result.navPerUnit().signum() <= 0) {
    throw new IllegalStateException("NAV per unit must be positive");
  }

  // New: NAV change within threshold
  BigDecimal previousNav = getPreviousNav(result.fund(), result.calculationDate());
  if (previousNav != null) {
    BigDecimal changePercent = calculateChangePercent(result.navPerUnit(), previousNav);
    if (changePercent.abs().compareTo(NAV_CHANGE_THRESHOLD_PERCENT) > 0) {
      log.warn("NAV change exceeds threshold: change={}%, threshold={}%",
               changePercent, NAV_CHANGE_THRESHOLD_PERCENT);
    }
  }
}
```

## Data Flow After Implementation

```
┌─────────────────────────────────────────────────────────────┐
│                     Daily Schedule                          │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  11:00  SEB Position Import                                 │
│         ├── Fetch CSV from S3                               │
│         ├── Parse into FundPosition                         │
│         ├── Save to investment_fund_position                │
│         └── Record to Ledger (NavPositionLedger)  ◄── NEW   │
│                                                             │
│  06:00  Fee Calculation                                     │
│         ├── Calculate management/custody fees               │
│         ├── Save to investment_fee_accrual                  │
│         └── Record to Ledger (NavFeeAccrualLedger) ◄── NEW  │
│                                                             │
│  15:30  NAV Calculation                                     │
│         ├── Read ALL inputs from Ledger  ◄── CHANGED        │
│         ├── Calculate NAV                                   │
│         ├── Validate result                                 │
│         └── Publish to index_values                         │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

## Ledger Transaction Examples

### Recording Securities Value (daily position update)
```
Transaction: POSITION_UPDATE
Date: 2026-01-28
Metadata: {fund: TKF100, reportDate: 2026-01-28, source: SEB}

Entries:
  SECURITIES_VALUE  +900,000.00 EUR  (debit - increase asset)
  NAV_EQUITY        -900,000.00 EUR  (credit - equity account)
```

### Recording Cash Position
```
Transaction: POSITION_UPDATE
Date: 2026-01-28
Metadata: {fund: TKF100, reportDate: 2026-01-28, source: SEB, type: CASH}

Entries:
  CASH_POSITION     +50,000.00 EUR  (debit - increase asset)
  NAV_EQUITY        -50,000.00 EUR  (credit - equity account)
```

### Recording Management Fee Accrual
```
Transaction: FEE_ACCRUAL
Date: 2026-01-28
Metadata: {fund: TKF100, feeType: MANAGEMENT, rate: 0.0016}

Entries:
  NAV_EQUITY               +43.84 EUR  (debit - decrease equity)
  MANAGEMENT_FEE_ACCRUAL   -43.84 EUR  (credit - increase liability)
```

**Note:** Position changes are recorded as deltas (difference from previous day), not absolute values. This ensures the ledger balance always reflects current positions.

## Design Decisions

1. **Equity Account**: ✅ Create dedicated `NAV_EQUITY` account to balance all position entries

2. **Historical Data**: ✅ Start fresh from fund launch (Feb 1, 2026) - no backfill needed

3. **Timing**: Fee calculation (06:00) → Position import (11:00/15:00) → NAV (15:30) - sequence is correct

4. **Rollback**: No automatic rollback - ledger entries are immutable, corrections via new transactions

**Launch Date: February 1, 2026** - Ledger tracking starts from day one of fund operations.

## Files to Create/Modify

### Create
- `NavPositionLedger.java` - Records position data to ledger
- `NavFeeAccrualLedger.java` - Records fee accruals to ledger
- `NavLedgerReconciliation.java` - Validates ledger vs sources

### Modify
- `SystemAccount.java` - Add 7 new accounts
- `FundPositionImportJob.java` - Call NavPositionLedger
- `FeeCalculationScheduledJob.java` - Call NavFeeAccrualLedger
- `SecuritiesValueComponent.java` - Read from ledger
- `CashPositionComponent.java` - Read from ledger
- `ReceivablesComponent.java` - Read from ledger
- `PayablesComponent.java` - Read from ledger
- `ManagementFeeAccrualComponent.java` - Read from ledger
- `CustodyFeeAccrualComponent.java` - Read from ledger
- `NavCalculationService.java` - Add validations, cleanup

## Verification

1. Unit tests for each new/modified component
2. Integration test: Full flow from position import → ledger → NAV
3. Reconciliation test: Ledger matches external sources
4. Manual verification: Compare NAV with spreadsheet calculation
