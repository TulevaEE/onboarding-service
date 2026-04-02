#!/usr/bin/env bash
set -euo pipefail

# Exports a transaction pipeline test snapshot from a PostgreSQL database.
#
# Usage:
#   ./export-snapshot.sh <postgres-url> <fund-code> <date>
#
# Example:
#   ./export-snapshot.sh postgres://user:pass@host:5432/onboarding TKF100 2026-02-10
#
# Prerequisites: psql (PostgreSQL client)

DB_URL="${1:?Usage: $0 <postgres-url> <fund-code> <date>}"
FUND="${2:?Usage: $0 <postgres-url> <fund-code> <date>}"
TARGET_DATE="${3:?Usage: $0 <postgres-url> <fund-code> <date>}"

case "$FUND" in
  TKF100) FUND_ISIN="EE0000003283" ;;
  *) echo "Unknown fund: $FUND. Add its ISIN to this script." >&2; exit 1 ;;
esac

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
OUTPUT="$SCRIPT_DIR/${FUND}_${TARGET_DATE}.sql"

command -v psql >/dev/null 2>&1 || { echo "psql not found" >&2; exit 1; }

MISSING=()

sql() { psql "$DB_URL" -X -t -A -q -c "$1"; }

sql_section() {
  local label=$1 query=$2
  local result
  result=$(sql "$query")
  if [[ -z "$result" ]]; then
    MISSING+=("$label")
    echo "-- $label: *** EMPTY - no data found ***"
  else
    echo "$result"
  fi
}

echo "Exporting: fund=$FUND date=$TARGET_DATE isin=$FUND_ISIN"

{
  echo "-- Transaction pipeline snapshot: $FUND as of $TARGET_DATE"
  echo "-- Exported by export-snapshot.sh on $(date +%Y-%m-%d)"
  echo ""

  # -- 1. Fund position (cash) --
  sql_section "investment_fund_position" "
    SELECT '-- investment_fund_position (cash balance)' || E'\n' ||
           'INSERT INTO investment_fund_position (nav_date, fund_code, account_type, account_name, market_value, currency, created_at)' || E'\n' ||
           'VALUES' || E'\n' ||
           string_agg(
             format('  (DATE %L, %L, %L, %L, %s, %L, CURRENT_TIMESTAMP)',
               nav_date::text, fund_code, account_type, account_name, market_value, currency),
             E',\n' ORDER BY account_name
           ) || ';'
    FROM investment_fund_position
    WHERE fund_code = '$FUND' AND nav_date = '$TARGET_DATE' AND account_type = 'CASH'
    HAVING count(*) > 0
  "
  echo ""

  # -- 3. Fee accruals --
  sql_section "investment_fee_accrual" "
    SELECT '-- investment_fee_accrual' || E'\n' ||
           'INSERT INTO investment_fee_accrual (fund_code, fee_type, accrual_date, fee_month, base_value, annual_rate, daily_amount_net, daily_amount_gross, vat_rate, days_in_year, reference_date)' || E'\n' ||
           'VALUES' || E'\n' ||
           string_agg(
             format('  (%L, %L, DATE %L, DATE %L, %s, %s, %s, %s, %s, %s, %s)',
               fund_code, fee_type, accrual_date::text, fee_month::text,
               base_value, annual_rate, daily_amount_net, daily_amount_gross,
               COALESCE(vat_rate::text, 'NULL'), days_in_year,
               CASE WHEN reference_date IS NOT NULL
                    THEN 'DATE ' || quote_literal(reference_date::text)
                    ELSE 'NULL' END),
             E',\n' ORDER BY fee_type, accrual_date
           ) || ';'
    FROM investment_fee_accrual
    WHERE fund_code = '$FUND'
      AND fee_month = date_trunc('month', DATE '$TARGET_DATE')::date
      AND fee_type IN ('MANAGEMENT', 'DEPOT')
      AND accrual_date < '$TARGET_DATE'
    HAVING count(*) > 0
  "
  echo ""

  # -- 4. Model portfolio allocations --
  sql_section "investment_model_portfolio_allocation" "
    SELECT '-- investment_model_portfolio_allocation' || E'\n' ||
           'INSERT INTO investment_model_portfolio_allocation (effective_date, fund_code, isin, weight, instrument_type, order_venue, ticker, bbg_ticker, label, fast_sell, created_at)' || E'\n' ||
           'VALUES' || E'\n' ||
           string_agg(
             format('  (DATE %L, %L, %L, %s, %L, %L, %L, %L, %L, %s, CURRENT_TIMESTAMP)',
               effective_date::text, fund_code, isin, weight, instrument_type, order_venue,
               ticker, bbg_ticker, label,
               CASE WHEN fast_sell THEN 'true' ELSE 'false' END),
             E',\n' ORDER BY isin
           ) || ';'
    FROM investment_model_portfolio_allocation
    WHERE fund_code = '$FUND'
      AND effective_date = (
        SELECT MAX(effective_date)
        FROM investment_model_portfolio_allocation
        WHERE fund_code = '$FUND'
      )
      AND isin IS NOT NULL
    HAVING count(*) > 0
  "
  echo ""

  # -- 5. Fund limit --
  sql_section "investment_fund_limit" "
    SELECT '-- investment_fund_limit' || E'\n' ||
           format('INSERT INTO investment_fund_limit (effective_date, fund_code, reserve_soft, reserve_hard, min_transaction, created_at)' || E'\n' ||
                  'VALUES (DATE %L, %L, %s, %s, %s, CURRENT_TIMESTAMP);',
             effective_date::text, fund_code, reserve_soft, reserve_hard, min_transaction)
    FROM investment_fund_limit
    WHERE fund_code = '$FUND'
      AND effective_date = (
        SELECT MAX(effective_date)
        FROM investment_fund_limit
        WHERE fund_code = '$FUND'
      )
    LIMIT 1
  "
  echo ""

  # -- 6. Position limits --
  sql_section "investment_position_limit" "
    SELECT '-- investment_position_limit' || E'\n' ||
           'INSERT INTO investment_position_limit (effective_date, fund_code, isin, soft_limit_percent, hard_limit_percent, created_at)' || E'\n' ||
           'VALUES' || E'\n' ||
           string_agg(
             format('  (DATE %L, %L, %L, %s, %s, CURRENT_TIMESTAMP)',
               effective_date::text, fund_code, isin, soft_limit_percent, hard_limit_percent),
             E',\n' ORDER BY isin
           ) || ';'
    FROM investment_position_limit
    WHERE fund_code = '$FUND'
      AND effective_date = (
        SELECT MAX(effective_date)
        FROM investment_position_limit
        WHERE fund_code = '$FUND'
      )
    HAVING count(*) > 0
  "
  echo ""

  # -- 7. Ledger balances --
  fund_units=$(sql "SELECT COALESCE(SUM(e.amount), 0) FROM ledger.entry e JOIN ledger.account a ON e.account_id = a.id WHERE a.name = 'FUND_UNITS_RESERVED' AND a.purpose = 'USER_ACCOUNT' AND a.asset_type = 'FUND_UNIT'")
  unreconciled=$(sql "SELECT COALESCE(SUM(e.amount), 0) FROM ledger.entry e JOIN ledger.account a ON e.account_id = a.id WHERE a.name = 'UNRECONCILED_BANK_RECEIPTS' AND a.purpose = 'SYSTEM_ACCOUNT'")
  clearing=$(sql "SELECT COALESCE(SUM(e.amount), 0) FROM ledger.entry e JOIN ledger.account a ON e.account_id = a.id WHERE a.name = 'INCOMING_PAYMENTS_CLEARING' AND a.purpose = 'SYSTEM_ACCOUNT'")

  echo "-- ledger data (balances: FUND_UNITS_RESERVED=$fund_units, UNRECONCILED=$unreconciled, CLEARING=$clearing)"
  cat <<'LEDGER'
INSERT INTO ledger.party (id, party_type, owner_id, details)
VALUES ('00000000-0000-0000-0000-000000000099', 'LEGAL_ENTITY', 'TEST_OWNER', '{}');

INSERT INTO ledger.account (id, name, purpose, account_type, asset_type, owner_party_id)
VALUES ('00000000-0000-0000-0000-000000000001', 'FUND_UNITS_RESERVED', 'USER_ACCOUNT', 'ASSET', 'FUND_UNIT', '00000000-0000-0000-0000-000000000099');

INSERT INTO ledger.account (id, name, purpose, account_type, asset_type)
VALUES
  ('00000000-0000-0000-0000-000000000002', 'UNRECONCILED_BANK_RECEIPTS', 'SYSTEM_ACCOUNT', 'ASSET', 'EUR'),
  ('00000000-0000-0000-0000-000000000003', 'INCOMING_PAYMENTS_CLEARING', 'SYSTEM_ACCOUNT', 'ASSET', 'EUR');
LEDGER

  counter_n=10
  txn_n=20
  entry_n=30

  negate() { echo "$1" | awk '{printf "%g", -$1}'; }

  emit_balance() {
    local acct_id=$1 balance=$2 asset=$3 counter_name=$4
    if echo "$balance" | awk '{ exit ($1 == 0) }'; then
      local cid tid e1 e2 neg_balance
      cid=$(printf '00000000-0000-0000-0000-%012d' $counter_n)
      tid=$(printf '00000000-0000-0000-0000-%012d' $txn_n)
      e1=$(printf '00000000-0000-0000-0000-%012d' $entry_n)
      e2=$(printf '00000000-0000-0000-0000-%012d' $((entry_n + 1)))
      neg_balance=$(negate "$balance")

      cat <<EOF

INSERT INTO ledger.account (id, name, purpose, account_type, asset_type)
VALUES ('$cid', '$counter_name', 'SYSTEM_ACCOUNT', 'ASSET', '$asset');

INSERT INTO ledger.transaction (id, transaction_type, transaction_date, metadata)
VALUES ('$tid', 'TRANSFER', TIMESTAMP '$TARGET_DATE 00:00:00', '{}');

INSERT INTO ledger.entry (id, account_id, transaction_id, amount, asset_type)
VALUES
  ('$e1', '$acct_id', '$tid', $balance, '$asset'),
  ('$e2', '$cid', '$tid', $neg_balance, '$asset');
EOF
      counter_n=$((counter_n + 1))
      txn_n=$((txn_n + 1))
      entry_n=$((entry_n + 2))
    fi
  }

  emit_balance "00000000-0000-0000-0000-000000000001" "$fund_units"   "FUND_UNIT" "COUNTER_FUND_UNITS"
  emit_balance "00000000-0000-0000-0000-000000000002" "$unreconciled" "EUR"       "COUNTER_UNRECONCILED"
  emit_balance "00000000-0000-0000-0000-000000000003" "$clearing"     "EUR"       "COUNTER_CLEARING"
  echo ""

  # -- 8. NAV --
  sql_section "index_values (NAV)" "
    SELECT '-- index_values (NAV)' || E'\n' ||
           format('INSERT INTO index_values (key, date, value, provider, updated_at)' || E'\n' ||
                  'VALUES (%L, DATE %L, %s, %L, TIMESTAMP %L);',
             key, date::text, value, provider,
             to_char(updated_at AT TIME ZONE 'UTC', 'YYYY-MM-DD HH24:MI:SS'))
    FROM index_values
    WHERE key = '$FUND_ISIN'
    ORDER BY date DESC NULLS LAST
    LIMIT 1
  "
} > "$OUTPUT"

echo ""
echo "Done: $OUTPUT"

if [[ ${#MISSING[@]} -gt 0 ]]; then
  echo ""
  echo "WARNING: The following sections returned no data:"
  for m in "${MISSING[@]}"; do
    echo "  - $m"
  done
  echo ""
  echo "The test will fail without this data. Check the production database"
  echo "or add the missing rows manually to the output file."
fi

echo ""
echo "Next steps:"
echo "  1. Review the file: less $OUTPUT"
echo "  2. Update TEST_DATE in TransactionPipelineIntegrationTest.java to $TARGET_DATE"
echo "  3. Update @Sql path to: classpath:db/snapshots/transaction-pipeline/${FUND}_${TARGET_DATE}.sql"
echo "  4. Run: ./gradlew test --tests TransactionPipelineIntegrationTest --rerun"
