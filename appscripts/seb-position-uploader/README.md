# seb-position-uploader

Google Apps Script that polls the `funds@tuleva.ee` Gmail mailbox for SEB position-report attachments and uploads them to the `tuleva-investment-reports` S3 bucket. The Spring Boot `ReportImportJob` in this repo then pulls those files and ingests them into `investment_report` / `investment_fund_position`. The exact sender allowlist lives in `src/Code.js` (`SOURCES`).

This script lives in [Google Apps Script](https://script.google.com), not on our servers. It runs unattended every 5 minutes via a Google time-driven trigger. The source of truth is `src/Code.js` in this repo; the live editor copy is kept in sync via `clasp push`.

## Apps Script project

- Project name: **Investment CSV to S3**
- Script ID: `1cpcI-qO0ZcCWqb6XdS0AjxE82N_F3rF926nhxyfgZlu3taeKD5qWLA8l`
- Owner: shared `funds@tuleva.ee` Google account (credentials in the team password manager). **Never run `clasp login` from a personal Google account** — see "Deploying" below.

## Local development

```bash
cd appscripts/seb-position-uploader
npm ci
npm test
```

Tests are pure-Jest — no Apps Script runtime needed. The `module.exports` block at the bottom of `src/Code.js` is a no-op shim that lets Node `require` the file; it's invisible to the Apps Script V8 runtime.

## Deploying

Manual deploy via `clasp` until the CircleCI integration lands (see plan milestone M5). Always sign in to the shared `funds@tuleva.ee` account first:

1. Sign out of any personal Google session in your browser (or use a fresh profile).
2. Sign in to `funds@tuleva.ee` using credentials from the team password manager.
3. Verify that "Investment CSV to S3" is visible at https://script.google.com — if not, you're signed in as the wrong user.
4. From this directory:
   ```bash
   npx clasp login           # browser flow, sign in as funds@tuleva.ee
   npx clasp push --force    # overwrites the live Code.gs with our src/Code.js
   ```

## Apps Script Properties (NOT in this repo)

The script reads these from `PropertiesService.getScriptProperties()` at runtime. They are configured manually in the Apps Script editor (Project Settings → Script properties) and are intentionally **not** stored in git:

- `AWS_ACCESS_KEY_ID`
- `AWS_SECRET_ACCESS_KEY`
- `AWS_REGION` (defaults to `eu-central-1`)

Future additions (per the plan):

- `SLACK_WEBHOOK_URL` — incoming webhook scoped to `#tech` for error/skip alerts (added in milestone M4a/M4b).
