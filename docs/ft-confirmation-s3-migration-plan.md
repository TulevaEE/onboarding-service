# FT Confirmation Ingestion — S3-Drop Migration Plan

Realizes **ADR 0001** (`adr-0001-external-integration-via-s3-drop.md`) for the FT (First Trust /
Allfunds) trade-confirmation feed. Moves FT from **GAS-push** (`POST /admin/transaction-registry/
ft-confirmations`) to **S3-drop + Java PDFBox parse**, mirroring how SEB/Swedbank reports arrive.

## What changes, what doesn't

- **Unchanged:** `FtConfirmationVerificationService.verifyAll(List<FtConfirmation>, actor)` and
  everything downstream — order matching, `FtConfirmationAuditRecorder` (dedup), `FtConfirmationDigest`,
  `PositionPriceResolver`. Only the **entry** changes: from an HTTP POST of parsed rows to an S3-pull
  job that parses PDFs into the same `FtConfirmation` objects.
- **Removed at the end (M5):** the two push endpoints + their DTOs, and the GAS PDF parser + trigger.

## Key design decisions

1. **Standalone FT ingest, not the CSV `ReportSource` framework.** FT is *many PDFs under a prefix*
   (not one CSV per date), PDF not CSV, and calls `verifyAll` directly (not via `ReportImportCompleted`).
   Build a dedicated job in `transaction.ingest` that mirrors the *conventions* of `ReportImportJob`
   (`S3Client`, `@Scheduled` + `@SchedulerLock`, injected `Clock`, `s3LastModified` idempotency) without
   reusing the CSV-specific `ReportSource`/`InvestmentReportService.saveReport` path.
2. **Parse with Apache PDFBox `PDFTextStripper`** (add explicit `org.apache.pdfbox:pdfbox`; only the
   `openhtmltopdf-pdfbox` *generator* is present today). Port GAS's label→value strategy: `extractField`
   matches `Label: Value` on the same line or `Label⏎Value` on the next, case-insensitive.
3. **SES inbound → S3** stores the raw email; a Java `jakarta.mail` seam extracts the PDF attachment,
   then PDFBox parses it. (Alternative: SES → Lambda → clean PDF to S3, and the job reads PDFs directly.
   Not recommended — keep the logic in Java per the ADR.)
4. **Idempotency** rides on the existing audit dedup (`recordOutcome` returns `recorded=false` on a
   repeat) + digest dedup, so re-parsing the same PDF on a later poll records or alerts nothing twice.
   Optionally track processed S3 keys to skip re-parse (efficiency only; no migration if we skip it).
5. **Reuse the `tuleva-investment-reports` bucket** under a new prefix (e.g. `ft-confirmations/`) — same
   IAM/access as SEB/Swedbank.

## Risks / unknowns

- **R1 — text layer vs OCR (GATING) — ✅ RESOLVED 2026-07-23.** Tested `pdftotext` against a real Flow
  Traders confirmation (`MID9BlFbos-00`): **the text layer extracts perfectly** — every field clean, no
  garbling. So **PDFBox `PDFTextStripper` reads it directly; no OCR / no Tesseract.** (GAS OCRs only because
  Apps Script has no PDF-text API — not because the PDF needs it.) Contrast: the SEB *fund* confirmation's
  text layer is broken (font-encoding) and would need OCR — but SEB is out of scope (covered by the pending CSV).
- **R2 — `Account` → `TulevaFund` mapping — partially resolved.** `Account` is the **full English fund
  name**. Confirmed from the sample: `Tuleva Additional Investment Fund` → **TKF100**. Still need the exact
  `Account` strings for **TUK75** (Maailma Aktsiate) and **TUV100** (III Samba) — get from one confirmation
  each, or map from the known fund set. `TulevaFund.fromCode` exists; add a name/account lookup.
- **R3 — SES routing (infra, J-owned).** Source is now concrete: **`from:flowtraders@ullink.eu`,
  `subject:"Trade Confirmation"`, PDF attachment** (per GAS `SEB_raport_import_convert.gs` OSA 3). Cleanest
  path: a **Gmail filter** on that sender/subject **auto-forwards to an SES-inbound address** (e.g.
  `ft-confirmations@in.tuleva.ee`, MX → SES) → SES stores raw email to S3. Replaces the GAS Drive-save;
  mailbox stays put.
- **R4 — formats — ✅ confirmed against the sample.** `Trade Date: 20260717` (`yyyyMMdd`, use directly for
  `tradeDate`); `Trade Date Time: 20260717-09:32:44 UTC`; `Quantity: 1,047` (strip comma); `Gross Price:
  56.850000 EUR` (strip ` EUR`, 6 dp); `Your Direction: Buy`; `Allocation ID: MID9BlFbos-00`; cancellation
  from confirmation text. `tradeDate` is the **UTC** `LocalDate` (matches the existing `hasTradeDate`).

## Confirmed field layout (sample `MID9BlFbos-00`, Flow Traders B.V., 2026-07-17)

`Label:  Value`, one field per line. Fields present: `Allocation ID`, `Counterparty` (=`TULEVA FONDID AS`),
`Account`, `Trade Date Time`, `Trade Date`, `Settlement Date`, `Security Description`, `Bloomberg Ticker`,
`ISIN`, `Your Direction`, `Quantity`, `Gross Price ... EUR`, `Net Price`, `Transaction Cost`, `Net Amount`,
`Settlement Currency`, plus settlement-instruction blocks and a legal footer (ignored). Contact:
`midoffice.amsterdam@nl.flowtraders.com`. The ported `extractField` (`Label\s*:\s*(value)`) handles it.

## Milestones (TDD-first, refactor/add before delete)

### M1 — FT PDF parser in Java (additive, no wiring)
- **Prereq:** ≥1 real sample confirmation PDF (+ ideally one cancellation) — resolves R1/R2/R4.
- **Failing test (business outcome):** given a real FT confirmation PDF, the parser yields the correct
  `FtConfirmation` (fund, isin, UTC tradeDate, quantity, grossPrice, type, account [+ allocationId]).
- **Steps:** add the pdfbox dependency; `FtConfirmationPdfParser` = `PDFTextStripper` → text → ported
  `extractField` regex → field mapping (UTC dates, strip ` EUR`/thousands, `Account`→`TulevaFund`,
  cancellation via `/trade cancellation/i`); unit test per field + cancellation + **malformed → throws**
  (fail loud, per the economics policy — never substitute).
- **If R1 fails (image-only):** stop and escalate the Tesseract decision before writing more.

### M2 — SES → S3 landing (infra J-owned + a Java MIME seam)
- SES inbound receipt rule writes the raw email under `ft-confirmations-raw/` in the reports bucket.
- `FtEmailPdfExtractor` (`jakarta.mail`): raw MIME → PDF attachment bytes. TDD against a saved raw-email
  fixture (scrubbed). *(If we pick SES→Lambda→clean-PDF instead, this Java seam drops and the job reads
  PDFs directly.)*

### M3 — S3 pull job (additive; shadow mode, no alerts)
- `FtConfirmationS3Source`: `ListObjectsV2` under the prefix (net-new — no existing list usage), get
  object, HeadObject `lastModified`.
- `FtConfirmationImportJob`: `@Scheduled` + `@SchedulerLock`, `Clock`-injected, behind an enable flag
  (`@ConditionalOnProperty`); list new objects → extract PDF (M2) → parse (M1) → `verifyAll`. Idempotent
  via audit dedup.
- **Failing integration test:** a PDF object in a mocked/LocalStack S3 prefix → verified outcome recorded;
  second run → no duplicate audit/alert. Confirm the audit dedup key holds across both entry paths (push
  + job) during the parallel run.

### M4 — Parallel run + verification
- Deploy M1–M3. FT PDFs land in S3 and the job verifies them alongside the live GAS push. Compare audit
  outcomes for agreement over a period. No flag flip needed (already shadow mode).

### M5 — Retire the push (behavior change; its own PR, delete LAST)
1. Remove the GAS `pushFtConfirmationsToRegistry` trigger (tuleva repo, `clasp`) **first**, so nothing
   still POSTs.
2. Delete `POST /admin/transaction-registry/ft-confirmation` (singular) + `.../ft-confirmations` (plural)
   + their request DTOs/tests. Keep the domain `FtConfirmation` + `verifyAll` (now job-fed). The
   `X-Admin-Actor` on those endpoints goes away with them.
3. Remove the GAS parser `tehingukinnitused_parse.gs` + Drive/OCR plumbing (tuleva repo) once the S3 path
   is proven.

## Testing & conventions
- **No PII in fixtures** (CLAUDE.md): scrub sample PDFs/emails — synthetic names, opaque IDs.
- Unit tests per field (M1); LocalStack/mocked-S3 integration test (M3) — object → verify → idempotent
  re-run. Full suite green each commit (pre-commit hook).
- **No migration expected.** Only needed if we add a processed-keys table or an `allocationId` column —
  flag if so; next free migration is **V1_234** (master max is V1_233; V1_230–233 are non-registry).

## Optional improvement the port unlocks
The current `FtConfirmation` has **no `allocationId`** — dedup is attribute-based (isin/date/qty/price).
Parsing the PDF ourselves recovers FT's stable `Allocation ID`; carrying it into the DTO would make dedup
robust. Deferred: changing the dedup key causes a one-time re-alert at transition (like the #1764
null-`dedup_key` note), so decide separately from the core port.

## Rollback
Until M5 the GAS push stays the live path and the S3 job is additive/shadow; disabling the job (flag off)
reverts with no data loss (audit dedup).
