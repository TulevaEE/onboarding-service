# ADR 0001 — External systems integrate via S3 file-drop, not by calling our HTTP API

**Status:** Accepted — 2026-07-23
**Deciders:** J (product/eng owner), engineering
**Applies to:** the transaction-registry pipeline and every future external data feed into onboarding-service

## Context

onboarding-service ingests data from several external / automated sources:

- **SEB** pending-transaction and position CSVs
- **Swedbank** position CSVs
- **First Trust / Allfunds ("FT")** trade confirmations (PDF, delivered by email)
- **EPIS** reports R16 / R17 / R21 / R45 (no API — a human fetches them from the EPIS portal)

SEB and Swedbank already follow one consistent shape: the external system **drops a file in
S3**, and onboarding-service **pulls and parses it** on a schedule
(`ReportImportJob` + `SebReportSource` / `SwedbankReportSource`, landing raw bytes in
`investment_report.raw_data`). That shape is durable, replayable, decoupled from our uptime,
and authenticated by IAM.

**FT confirmations broke the pattern.** A Google Apps Script (GAS) parses the confirmation PDF
and **pushes** the parsed rows to our HTTP API
(`POST /admin/transaction-registry/ft-confirmations`, gated by a shared `X-Admin-Token` plus a
self-declared `X-Admin-Actor`). This was an interim bridge — it reused the PDF parser that
already existed in GAS to get FT into shadow-mode verification quickly. But a machine calling
our admin API fragments the ingestion architecture (two mechanisms, two failure modes, two auth
models) and is what created the forgeable-`actor` / shared-admin-token coupling in the audit
trail. We keep re-discovering that this endpoint "feels wrong"; this ADR records why, once.

## Decision

**Automated / external systems integrate with onboarding-service by dropping files into S3,
which onboarding-service pulls and processes. They MUST NOT call our HTTP endpoints to push data
in.**

GAS does not call the Java app. Where GAS currently owns parsing or format logic for an inbound
feed, that logic is **ported into onboarding-service** (e.g. FT PDF parsing via PDFBox) and the
raw file lands in S3 the same way SEB / Swedbank reports do. New integration work starts from
*"how does the file get to S3?"* — never *"which endpoint do we POST to?"*

## Scope — what this covers and what it does not

**IN (must be S3-drop, never push):**
- Any recurring data feed produced by an automated system — broker files, GAS-produced files,
  scheduled exports, etc.

**OUT (HTTP is correct and stays):**
- **Human-operator commands** — create / confirm / cancel a trade batch, trigger a calc or a
  reconciliation match. A person invoking an action is not a system pushing data; these stay as
  `/admin` HTTP endpoints (attribution handled by per-operator identity, separate decision).
- **Reads / dashboards** — status, daily summary, settlement warnings, export download.
- **Human bridge uploads where there is no automated source** — EPIS reports (`import-report`)
  and the one-time historical backfill (`import-history`). A human fetches these because the
  upstream has no API. Legitimate. Landing a durable raw copy in S3 for replayability is
  *encouraged* but not required, and their absence of an S3 drop is **not** a violation of this
  ADR.

## Consequences

- The FT confirmation push endpoints — `POST /admin/transaction-registry/ft-confirmation`
  (singular) and `.../ft-confirmations` (plural) — are to be **retired** once the S3-drop path
  is live and parallel-verified. They stay until then (shadow mode) so FT ingestion is never
  interrupted, and so the live GAS trigger keeps working until it is removed.
- **Attribution simplifies.** With no machine caller left on `/admin`, the only actors on those
  endpoints are human operators — which is what makes per-operator-token attribution clean (no
  awkward `gas-ft-push` service identity to special-case).
- Each S3-drop feed needs three parts in Java: the **parser**, an **S3 landing** for the raw
  file, and a **pull job / `*ReportSource`**. For email-delivered files (FT), the S3 landing is
  an infra prerequisite (an SES inbound-email rule → S3, or a scheduled mail-fetch job) and is a
  decision to make before that feed can go pull-based.

## Migration — FT confirmations (the one current violation)

Ordered so the working path is never broken (refactor/add first, delete last):

1. **Port the parser.** Port the GAS FT PDF parser (`apps/seb-import/tehingukinnitused_parse.gs`,
   the current format spec) to a Java **PDFBox** parser — TDD against the field spec (`Allocation
   ID`, `Trade Date Time` UTC, `Gross Price` `10.916000 EUR`, comma-thousands `Quantity`, etc.).
2. **Land the file in S3.** Decide + build the email→S3 landing for the confirmation PDF.
3. **Pull + parse.** Add an `FtConfirmationReportSource` + pull job that reads the PDF from S3
   and calls the existing `FtConfirmationVerificationService.verifyAll`. Confirm the `Allocation
   ID` dedup makes double-ingestion during parallel run idempotent.
4. **Parallel run** S3-pull alongside the GAS push until outcomes agree.
5. **Delete.** Remove the GAS `pushFtConfirmationsToRegistry` trigger, then delete the two push
   endpoints and their HTTP DTOs — keeping the domain `FtConfirmation` type + `verifyAll`, now
   fed by the job.

## References

- Gap doc §4.1 (FT confirmation ingestion — "End state, STILL OPEN: port PDF parsing into
  onboarding-service; FT email → S3, the way SEB reports arrive; retire the GAS parser").
- `ReportImportJob`, `SebReportSource`, `SwedbankReportSource` — the pull pattern to mirror.
