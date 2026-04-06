# BlackRock Fee/Rebate Adjustments

BlackRock fees and rebates must be recorded daily to the ledger so that NAV calculations are correct. This is done via
an admin API endpoint.

## Which funds need adjustments?

| Fund   | Type              | CSV column               |
|--------|-------------------|--------------------------|
| TUK75  | Rebate (positive) | "Other receivables"      |
| TUV100 | Rebate (positive) | "Other receivables"      |
| TUK00  | Fee (negative)    | "Liabilities Other"      |
| TKF100 | TBD               | Check spreadsheet        |

## How it works

The endpoint accepts a **target balance** — the total BlackRock adjustment amount that should appear in the NAV for a
given fund and date. The system computes the delta from whatever is currently in the ledger and records it. This means:

- Re-submitting the same value is safe (no duplicate entries)
- Corrections work automatically (submit the corrected value, the system records the difference)

## Recording adjustments

Use the ops token (ask Erko if you don't have it).

```bash
# Template
curl -X POST 'https://onboarding-service.tuleva.ee/admin/blackrock-adjustment?fundCode=FUND&amount=AMOUNT&date=DATE' \
  -H 'X-Admin-Token: <ops-token>'
```

### Example: recording April 2, 2026 values from the NAV spreadsheet

```bash
# TUK75 — "Other receivables" = 38531.70
curl -X POST 'https://onboarding-service.tuleva.ee/admin/blackrock-adjustment?fundCode=TUK75&amount=38531.70&date=2026-04-02' \
  -H 'X-Admin-Token: <ops-token>'

# TUK00 — "Liabilities Other" = -2295.25 (note: negative!)
curl -X POST 'https://onboarding-service.tuleva.ee/admin/blackrock-adjustment?fundCode=TUK00&amount=-2295.25&date=2026-04-02' \
  -H 'X-Admin-Token: <ops-token>'

# TUV100 — "Other receivables" = 18839.14
curl -X POST 'https://onboarding-service.tuleva.ee/admin/blackrock-adjustment?fundCode=TUV100&amount=18839.14&date=2026-04-02' \
  -H 'X-Admin-Token: <ops-token>'
```

### Response

```json
{
  "fund": "TUK75",
  "date": "2026-04-02",
  "previousBalance": 0,
  "targetBalance": 38531.70,
  "delta": 38531.70,
  "transactionCreated": true
}
```

If `delta` is 0 and `transactionCreated` is false, the balance was already correct.

## Verifying NAV after adjustments

After recording adjustments, verify the NAV matches the spreadsheet:

```bash
curl -X POST 'https://onboarding-service.tuleva.ee/admin/calculate-nav?fundCode=TUK75&publish=false&date=2026-04-02' \
  -H 'X-Admin-Token: <ops-token>'
```

Compare `navPerUnit` and `blackrockAdjustment` in the response with the spreadsheet values.

## Using Claude Code

You can ask Claude Code to do this for you:

> Record the BlackRock adjustments from the NAV spreadsheet at /path/to/spreadsheet.csv for date 2026-04-02. The ops
> token is in .env as ADMIN_OPS_TOKEN. After recording, verify the NAV matches.

Claude Code will read the CSV, extract the values, make the API calls, and verify the results.
