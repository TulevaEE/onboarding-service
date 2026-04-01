# Transaction Pipeline Snapshots

SQL snapshots of production data used by `TransactionPipelineIntegrationTest`.

## Quick start

```bash
./src/test/resources/db/snapshots/transaction-pipeline/export-snapshot.sh \
  postgres://user:pass@host:5432/onboarding \
  TKF100 \
  2026-02-10
```

This connects to the database, runs all the needed queries, and writes an
H2-compatible SQL file to this directory.

## What the script does

1. Exports 7 tables filtered by fund and date:
   - `investment_fund_position` -- cash balance
   - `investment_fee_accrual` -- accrued fees for the month
   - `investment_model_portfolio_allocation` -- target weights
   - `investment_fund_limit` -- reserve and min transaction limits
   - `investment_position_limit` -- per-position soft/hard limits
   - `ledger.*` -- account balances (FUND_UNITS_RESERVED, UNRECONCILED_BANK_RECEIPTS, INCOMING_PAYMENTS_CLEARING)
   - `index_values` -- latest NAV for the fund

2. Formats everything as H2-compatible INSERT statements (DATE literals,
   TIMESTAMP literals, CURRENT_TIMESTAMP for created_at, unquoted booleans)

3. Generates synthetic ledger party/account/transaction/entry rows that
   produce the correct balance totals

## After exporting

1. Review the output file: `less TKF100_2026-02-10.sql`
2. In `TransactionPipelineIntegrationTest.java`:
   - Update `TEST_DATE` to match the snapshot date
   - Update the `@Sql` annotation path to point to the new file
3. Run: `./gradlew test --tests TransactionPipelineIntegrationTest --rerun`
4. Update the assertion values if the new data produces different order amounts

## File naming

`{FUND}_{YYYY-MM-DD}.sql` -- e.g. `TKF100_2026-02-10.sql`

## Adding a new fund

Add the fund code and ISIN mapping to the `case` block in `export-snapshot.sh`.
