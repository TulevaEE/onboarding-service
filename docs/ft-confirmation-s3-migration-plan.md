# FT Confirmation Ingestion — S3-Drop Migration Plan

Realizes **ADR 0001** (`adr-0001-external-integration-via-s3-drop.md`) for the **FT = Flow Traders**
(ETF market-maker; sender `flowtraders@ullink.eu`) trade-confirmation feed. Moves FT from **GAS-push**
(`POST /admin/transaction-registry/ft-confirmations`) to **S3-drop + Java PDFBox parse** — landed the
same way SEB/Swedbank reports already arrive: via the existing **`investment_reports_gs.gs` GAS→S3
uploader** into `tuleva-investment-reports`, which `ReportImportJob` already consumes.

## What changes, what doesn't

- **Unchanged:** `FtConfirmationVerificationService.verifyAll(List<FtConfirmation>, actor)` and
  everything downstream — order matching, `FtConfirmationAuditRecorder` (dedup), `FtConfirmationDigest`,
  `PositionPriceResolver`. Only the **entry** changes: from an HTTP POST of parsed rows to an S3-pull
  job that parses PDFs into the same `FtConfirmation` objects.
- **Removed at the end (M5):** the two push endpoints + their DTOs, the GAS PDF parser + the GAS→Java
  push trigger, and the redundant GAS FT-PDF-to-Drive save.
- **GAS is NOT fully retired — and that's ADR-compliant.** It keeps one job: the `investment_reports_gs.gs`
  uploader dropping the raw FT PDF into S3 (a file drop, *not* an API call). The parsing logic moves to
  Java, which is the ADR's actual requirement. Fully retiring GAS from ingestion (SES inbound) is a
  possible far-future cleanup, not part of this migration.

## Key design decisions

1. **Standalone FT ingest, not the CSV `ReportSource` framework.** FT is *many PDFs under a prefix*
   (not one CSV per date), PDF not CSV, and calls `verifyAll` directly (not via `ReportImportCompleted`).
   Build a dedicated job in `transaction.ingest` that mirrors the *conventions* of `ReportImportJob`
   (`S3Client`, `@Scheduled` + `@SchedulerLock`, injected `Clock`, `s3LastModified` idempotency) without
   reusing the CSV-specific `ReportSource`/`InvestmentReportService.saveReport` path.
2. **Parse with Apache PDFBox `PDFTextStripper`** (add explicit `org.apache.pdfbox:pdfbox`; only the
   `openhtmltopdf-pdfbox` *generator* is present today). Port GAS's label→value strategy: `extractField`
   matches `Label: Value` on the same line or `Label⏎Value` on the next, case-insensitive.
3. **Land the PDF via the existing GAS→S3 uploader**, not new infra. `investment_reports_gs.gs` already
   polls `funds@tuleva.ee` and uploads matched attachments to `tuleva-investment-reports` with working
   SigV4 signing, per-thread idempotency labels, and historical backfill. FT confirmations **already
   arrive at `funds@tuleva.ee`** (confirmed), so this is a `SOURCES` config addition + two small tweaks
   (see M2) — no SES, no Lambda, no MIME parsing in Java (the job reads a clean PDF straight from S3).
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
- **R2 — `Account` → `TulevaFund` mapping — ✅ RESOLVED 2026-07-23 (10 real confirmations), with one item
  to confirm.** **The `Account` field is NOT uniformly named** — each fund uses a *different* convention, and
  none is derivable from the `TulevaFund.displayName`:
  | FT `Account` string | → fund | vs enum `displayName` |
  | --- | --- | --- |
  | `Tuleva Additional Investment Fund` | **TKF100** | enum = "Tuleva Täiendav Kogumisfond" (English marketing name, no match) |
  | `TULEVA III SAMBA PENSIONIFOND` | **TUV100** | enum = "Tuleva III Samba Pensionifond" (matches case-insensitively) |
  | `MAAKPE` | **TUK75** ⚠️ | enum = "Tuleva Maailma Aktsiate Pensionifond" (cryptic code, no match) |

  So the parser needs an **explicit FT-account-alias → fund table** (normalize by trim + case-insensitive),
  **failing loud (ORPHAN/error) on any unrecognized `Account`** so a new/changed variant surfaces instead of
  silently mis-mapping. `MAAKPE` is **not** in our codebase — inferred as TUK75 because its ISIN
  `IE000I9HGDZ3` is a TUK75 holding, it appears on the same date/price as the separate `III SAMBA`
  confirmation, and `MAAKPE` ≈ a contraction of *MAAilma aKtsiate PEnsionifond*. **Confirm with J / the FT
  account setup before shipping.** The sample is small (8× TKF100, 1× TUV100, 1× TUK75), so treat the alias
  table as extensible + fail-loud, not exhaustive.
- **R3 — S3 landing — ✅ RESOLVED 2026-07-23 (SES dropped).** No new email infra. The production
  `investment_reports_gs.gs` GAS→S3 uploader already does exactly this for every other feed, and the FT
  emails (`from:flowtraders@ullink.eu`, `subject:"Trade Confirmation"`) **already land in `funds@tuleva.ee`**
  (confirmed by J), which the uploader searches (`to:funds@tuleva.ee has:attachment`). So the landing is a
  `SOURCES` addition + the two M2 tweaks, not the SES/MX/forwarding project previously scoped here.
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

**Cancellation detection:** the title is always `Trade Confirmation` (not a reliable signal). The
discriminator is the body line — normal: `We confirm the following trade .`; cancellation: `We confirm the
following trade cancellation.` Detect the phrase `trade cancellation` (case-insensitive), matching GAS.
Real example in the sample set: `MID9AiyZwO-00` cancels a TKF100 buy of `IE000F60HVH9` @ 4.951500, re-booked
as `MID9AoF5Ik-01` @ 4.970000 (note the `-01` allocation-id suffix on the rebook).

## Milestones (TDD-first, refactor/add before delete)

### M1 — FT PDF parser in Java (additive, no wiring) — **ready to build; R1/R2/R4 resolved**
- **Samples in hand:** 11 real confirmations (10 in `/Users/j/scratch`, 1 uploaded) covering all 3 funds,
  a cancellation + its rebook, comma-thousands quantities, 6-dp prices. R1/R2/R4 all resolved above.
- **Failing test (business outcome):** given a real FT confirmation PDF, the parser yields the correct
  `FtConfirmation` (fund, isin, UTC tradeDate, quantity, grossPrice, type, account [+ allocationId]).
- **Steps:** add the pdfbox dependency; `FtConfirmationPdfParser` = `PDFTextStripper` → text → ported
  `extractField` regex → field mapping (UTC dates, strip ` EUR`/thousands, `Account`→`TulevaFund` via the
  fail-loud alias table, cancellation via `/trade cancellation/i`); unit test per field + cancellation +
  unmapped-account + **malformed → throws** (fail loud, per the economics policy — never substitute).

### M2 — extend the existing GAS→S3 uploader for FT (in `investment_reports_gs.gs`)
Add FT to the `SOURCES` config so the raw PDF lands at `ft-confirmations/<allocationId>.pdf`:
```js
FT: { senders: ["flowtraders@ullink.eu"],
      files: [{ pattern: /^(MID\w+-\d+)\.pdf$/i, s3Prefix: "ft-confirmations/", s3Suffix: ".pdf" }] }
```
Two real tweaks (the uploader was built for dated CSVs):
1. **Binary bytes.** `processMessage` (`:260`) reads `attachment.getDataAsString()` — fine for CSV text,
   **corrupts a PDF**. Use `attachment.getBytes()` (or `copyBlob()`) + content-type `application/pdf` into
   `uploadToS3` (which already accepts a byte payload for the SigV4 hash).
2. **Allocation-id keys, not date keys.** `getFileConfigAndDate` (`:290`) builds the key from a **date**
   captured in the filename (`prefix + yyyy-mm-dd + suffix`). FT filenames carry an **allocation id**
   (`MID9BlFbos-00`) and there are many per day → key must be `ft-confirmations/<allocationId>.pdf`.
   Add a per-file `keyFromCapture` (or similar) branch so `match[1]` is used verbatim as the key stem.
- **Backfill for free:** the same uploader's `processHistoricalEmails30Days()` (with the FT source added)
  bulk-lands historical confirmations — giving the full fixture set + every real `Account` alias (locks R2).
- The GAS change is small; validate with the uploader's existing `testUpload` path against a scrubbed PDF.

### M3 — Java S3 pull job (additive; shadow mode, no alerts)
- `FtConfirmationS3Source`: `ListObjectsV2` under `ft-confirmations/` (net-new — no existing list usage),
  get object, HeadObject `lastModified`.
- `FtConfirmationImportJob`: `@Scheduled` + `@SchedulerLock`, `Clock`-injected, behind an enable flag
  (`@ConditionalOnProperty`); list new objects → parse each PDF (M1) → `verifyAll`. The object is already a
  clean PDF (GAS dropped it), so no MIME/attachment extraction is needed. Idempotent via audit dedup.
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
3. Remove the GAS parser `tehingukinnitused_parse.gs` (OCR + sheet) and the now-redundant FT-PDF-to-Drive
   save in `SEB_raport_import_convert.gs` (OSA 3), once the S3 path is proven. **Keep** the new FT `SOURCES`
   entry in `investment_reports_gs.gs` — that S3 upload is the ongoing feed.

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
