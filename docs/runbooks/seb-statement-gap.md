# SEB statement gap

A SEB end-of-day statement did not reach the ledger, usually because the gateway was down during the fetch. The ledger then runs behind the bank until the statement is booked. The fix is to fetch the statement again through the normal pipeline, which most of the time happens by itself.

## Symptoms

All of these appear in the savings Slack channel, except the last.

- `Bank reconciliation FAILED` with a `diff` that stays the same from one `statementDate` to the next. A fixed difference means one stranded set of bookings. A difference that grows is a different problem.
- `SEB statements not booked in the ledger` at 09:10, listing accounts and dates of the last week.
- A day with no statement in the query under [Verify](#verify).

## Is it urgent?

Usually not; it can wait for working hours.

- **NAV is not affected.** NAV cash reads the `CASH_POSITION` ledger account, which `FundPositionLedgerService` fills from SEB's position report, not from bank statements. A stranded statement cannot move a NAV. It can only leave the bank-balance health check unable to compare that day, and a check that could not run does not block the import.
- **Savers are affected only by timing.** Their payments wait in `CREATED` until the statement is booked, with no money lost. `StuckPaymentAlertJob` logs those older than 36 hours at 09:05.

Two TKF100 cases do have a deadline. A deposit that arrived before a 16:00 cutoff but is still unbooked gets a later NAV, so book it before the next working day's 16:00. And `TransactionInputService` reads `INCOMING_PAYMENTS_CLEARING`, so book deposits before preparing TKF100 orders.

## Does it need a person?

`SebStatementFetchingScheduler` runs every 30 minutes from 04:00 to 23:30. It fetches yesterday's statement and any uncovered day of the 7 days before that, and stops the run if the gateway is down. A gap normally heals the same morning, and the re-fetched statement reconciles by itself 5 minutes after processing.

Step in when:

- The 09:10 message comes back the next day, or a date is still listed after 23:30.
- A statement has `failed_at` set. The scheduler treats that day as covered and never re-fetches it; the 09:10 message reports it and `STATEMENT_UNPROCESSABLE` goes to the investment channel. Find out why it failed before re-fetching: a statement that cannot be parsed will fail again.
- The day is more than 8 days old, which is beyond both the catch-up and the 09:10 report.

If every day is covered and processed and the difference remains, this is not a gap, and re-fetching will not help.

## Re-fetch

Set `ONBOARDING_SERVICE_ADMIN_TOKEN` in your environment first. The call is idempotent: payments dedupe by external id and ledger transactions by external reference, so overlapping ranges and accounts that were never missing are safe.

```bash
curl -fsS -X POST "https://onboarding-service.tuleva.ee/admin/fetch-seb-history?from=YYYY-MM-DD&to=YYYY-MM-DD" \
  -H "X-Admin-Token: $ONBOARDING_SERVICE_ADMIN_TOKEN"
```

- Without `fundCode` it fetches every fund's accounts. Add `&fundCode=TUK75` or `&account=DEPOSIT_EUR` to narrow it.
- `to` must be yesterday or earlier. The code does not check this; SEB rejects it and the call fails. The accounts are fetched one by one, so a failure stops the rest. Just run it again.
- Do not set `from` before an account's first statement. The scheduler never reaches back that far, because the pension ledgers seed their opening balance from that statement.
- The response `Fetched SEB history for [...]` means stored, not booked. Processing takes a minute or two.

A range produces one statement per account, and its closing balance is for the last day only. The bank-balance health check therefore cannot compare the earlier days of the range, and the same applies to a catch-up statement. To have such a day compared, fetch it on its own with `from` and `to` both set to that day. Nothing does this automatically, because the day already counts as covered.

## Verify

Read-only production access is enough. Statements per day:

```sql
select day::date, count(distinct m.account_iban) as accounts,
       count(*) filter (where m.processed_at is not null) as processed,
       count(*) filter (where m.failed_at is not null) as failed
from generate_series(current_date - 8, current_date - 1, interval '1 day') day
left join banking_message m on m.bank_type = 'SEB' and m.message_type = 'HISTORIC_STATEMENT'
  and m.statement_from <= day::date and m.statement_to >= day::date
group by 1 order by 1;
```

Ledger balances at the start of today, which must equal the bank closing balance of yesterday's statement in the `Bank reconciliation` messages:

```sql
select a.name, sum(e.amount)
from ledger.account a
join ledger.entry e on e.account_id = a.id
join ledger.transaction t on t.id = e.transaction_id
where a.owner_party_id is null and a.name like '%CLEARING:%'
  and t.transaction_date <= (date_trunc('day', now() at time zone 'Europe/Tallinn') at time zone 'Europe/Tallinn') at time zone 'UTC'
group by a.name order by a.name;
```

You are done when every account shows `Bank reconciliation OK` for the last re-fetched day and the 09:10 message no longer lists it.

## Do not

Do not use `/admin/adjustments` or any other manual ledger entry to force a balance. The ledger is immutable, and an adjustment hides the gap and then double-counts once the real statement is booked.

## Who can run this

- Production admin token: the head of product and technology and the portfolio manager (`TODO(owner)`: confirm the second holder has it).
- Verification: read-only production database access or SQL in Metabase.

The endpoint accepts only `ADMIN_API_TOKEN`. The ops token and per-operator tokens are refused. It does not exist on staging, so there is no rehearsal there.

Background: `work/riskikontroll/postmortemid/2026-09-14-seb-eod-statement-gap.md` in the private `tuleva` repo.
