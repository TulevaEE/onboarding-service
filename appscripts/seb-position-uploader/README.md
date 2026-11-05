# seb-position-uploader

Google Apps Script that polls the `funds@tuleva.ee` Gmail mailbox for report and trade-confirmation attachments and uploads them to the `tuleva-investment-reports` S3 bucket: SEB position and pending-transaction reports and Swedbank portfolio reports (read by `ReportImportJob` into `investment_report` / `investment_fund_position`), Flow Traders trade confirmations (`ft-confirmations/`, read by `FtConfirmationImportJob`) and SEB mutual-fund trade confirmations (`seb-confirmations/`). The exact sender allowlist lives in `src/Code.js` (`SOURCES`).

This script lives in [Google Apps Script](https://script.google.com), not on our servers. It runs unattended every 5 minutes via a Google time-driven trigger. The source of truth is `src/Code.js` in this repo; the live editor copy is kept in sync via `clasp push`.

## Drive copy for the Google Sheets NAV fallback

Every SEB position and pending-transaction report that goes to S3 is also saved to a Google Drive folder (`DRIVE_SEB_REPORT_FOLDER_ID`), where Tuleva's Google Sheets scripts read it for the manual NAV fallback. Drive therefore gets the same files as S3, from the same senders, including a corrected report the funds mailbox sends to itself.

- **Its own pass.** `processNewEmails` runs the Drive copy after the S3 pass, also when an upload fails: expired AWS keys or a rejected file stop S3 but not the Drive copy the fallback needs exactly then, and a message S3 could not upload is copied too. The run still fails with the S3 error afterwards, and `processNewEmails` alerts Slack about it. Every run holds the script lock for both passes, so an overlapping trigger or catch-up run cannot interleave; a trigger that finds the lock taken does nothing and the next one retries, and `processHistoricalEmails` refuses to start until the running one ends. Each run copies the reports of the e-mails from its own window that are not in Drive yet: the last 2 days for `processNewEmails`, 7 and 30 days for `processHistoricalEmails` and `processHistoricalEmails30Days`. Progress is the `drive_source` record per report (third point), not a Gmail label, so a Drive problem can neither stop nor repeat an S3 upload, and a report whose copy failed is retried by every run while the e-mail is within the last 2 days, without holding up the other report of its e-mail or the later e-mails of its thread; for an older one the alert asks to run `processHistoricalEmails` once the cause is fixed. The first run therefore copies the last two days' reports; replaying older e-mails never brings back older content (next point).
- **The folder.** It keeps the attachment unchanged plus a `<name>_US.csv` copy with decimal points and comma separators. The Sheets scripts pick the `_US.csv` with the newest date in its name, so each report type and date keeps one current `_US.csv`: a newer report overwrites its content in place, and the previous content is kept as `<name>_US_vN.csv`. The replaced original is renamed `<name>_vN.csv` with the same N. A copy resumed after a failure keeps the N it started with, so its old content is kept once and both files share N, and no file is ever renamed onto a name the folder already has. A newer e-mail that finds another e-mail's copy unfinished completes that version first: the original left current joins that copy's `_US_vN.csv`, or, when that copy never changed the `_US.csv`, the newer e-mail archives the current pair under the same N. A file belongs to a type and date by the same filename pattern the S3 upload uses; of two attachments for the same report in one e-mail, S3 and Drive keep the last one without a `_US` ending. An attachment whose name carries a version suffix of its own, such as `…_v2.csv`, is saved under that name without the suffix, the report S3 keys it as, so a `_vN` name in the folder is always a version this script archived. A report whose current `_US.csv` and original already hold its content gets no new version, so the files tuleva's earlier Gmail saver wrote to the same folder, and a copy resumed after a failure, are not versioned again. An older e-mail never replaces newer content, and the same e-mail resumes a copy that failed half way (next section). The new original is the last file written: tuleva's `seb-import` starts its Sheets import when the SEB report file names change, and its settlement recheck reads the last updated pending report of each date.
- **Newest e-mail wins, on S3 and in Drive.** A report named by its date (SEB reports, the Swedbank portfolio) keeps one file per date. A Script Property `report_source:<s3 key>` records the e-mail (date and Gmail id) whose report the file holds; it is written after each upload, and by the Drive pass before its first write. Before any upload, each run also records the newest e-mail already uploaded for each report, so a report uploaded before the record existed, or whose record write failed, still counts; when this scan cannot read a thread or a message, an older e-mail could pass for the newest, so that run uploads nothing to S3 and fails, while the Drive pass still copies the e-mails newer than every message the scan could not read (none, when it could not tell their time); the run also keeps the newest e-mail per report in memory for its S3 and Drive passes, so a record write that fails cannot let an older e-mail through in the same run. An older e-mail, for example from a catch-up replay or a retry, is skipped on S3 and in Drive and counts as done; the Drive pass takes the e-mails of its window from all threads newest first, by time and then Gmail id, so once a newer e-mail of a report has been tried in a run an older one does not write that report, and a message whose attachments it cannot read keeps every older e-mail out of Drive that run (every e-mail, when it cannot tell a message's time or list a thread's messages); of two e-mails with the same time the higher Gmail id wins. A second Script Property, `drive_source:<s3 key>`, records the e-mail the report's current Drive copy came from, with the version number while the copy is unfinished and without it once the copy is complete; the unfinished record is written before the copy's first Drive change, and a copy whose record cannot be written changes nothing and is retried by the next run: it tells each run which reports are already in Drive, so a retry of a message whose copy failed half way skips the reports already saved instead of versioning them again. Records whose e-mail is more than 45 days old are dropped. Flow Traders confirmations are named by allocation id and keep no record.
- **Alerts.** A failure, or a missing `DRIVE_SEB_REPORT_FOLDER_ID`, posts one Slack alert per problem per day. An attachment already named `…_US.csv` never goes to Drive, where the Sheets scripts would read it as converted, and goes to S3 only when its e-mail has no attachment for that SEB report without the `_US` ending (other senders' files go to S3 as they always did); the script logs it and alerts once per e-mail and attachment, naming the e-mail and asking for a resend only when the e-mail has no plain attachment for that report (a Script Property `us_name_alerted:<Gmail id>:<name hash>` records the delivered alert, dropped after 45 days). An alert Slack does not take, or one with no `SLACK_WEBHOOK_URL` set, stays in the log, is tried again by the next run while the e-mail is in its window, and holds up neither the run nor the later e-mails of its thread.

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

CircleCI deploys the script; merging to `master` is the deploy. `appscript-test` runs `npm test` on every branch. On `master`, `appscript-deploy` then runs `npx clasp push --force` with the shared `funds@tuleva.ee` account's clasp login, stored as `CLASPRC_JSON` in the `appscript` CircleCI context. A `master` commit that changes nothing under `appscripts/` skips the deploy.

### Changing `oauthScopes`

Adding a scope to `src/appsscript.json` needs a new authorization from `funds@tuleva.ee`. Until someone signed in as `funds@tuleva.ee` opens the project in the Apps Script editor, runs `processNewEmails` once and approves the new permissions, the time-driven trigger fails: no report reaches S3, and the script cannot post to Slack, because it never starts. Merge such a change only when that person is ready to authorize right after `appscript-deploy` finishes, and check the next trigger run under Executions.

### Manual deploy (fallback)

Only when the CircleCI deploy can't run, for example because the stored clasp login has expired. Afterwards, merge the same code to `master` so the repo stays the source of truth, and update `CLASPRC_JSON` if the login had expired. Always sign in to the shared `funds@tuleva.ee` account first:

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
- `DRIVE_SEB_REPORT_FOLDER_ID` — the Drive folder for the SEB report copies, the same folder tuleva's `seb-import` reads. `funds@tuleva.ee` needs edit access to it. When it isn't set, the script skips the Drive copy and alerts once a day.
- `SLACK_WEBHOOK_URL` — Slack incoming webhook for error and skip alerts. When it isn't set, the script only logs the alert.
