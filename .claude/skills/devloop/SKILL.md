---
name: devloop
description: Drive the LIVE Tuleva app as the engineer (their own ID code, phone-approved login), navigate a feature, screenshot it across mobile + desktop, annotate findings on the images, cross-check on-screen data against the live API, and propose fixes. Use to UX-review or verify a feature against real production data.
argument-hint: [feature e.g. "dashboard ux review"]
---

# Devloop

You are reviewing a real feature of the Tuleva pension app against **live production data**, as the
engineer who invoked you. `$ARGUMENTS` is a feature description (default first target: **dashboard
UX review** on `pension.tuleva.ee`). You authenticate with the engineer's own Estonian ID code, they
approve on their phone, then you screenshot the feature and grade it — both the visuals and whether
the numbers on screen match the API.

Everything defaults to **live** (`pension.tuleva.ee`). Use staging or local **only** when the engineer
explicitly asks (`--env staging` / `--env local`).

Tooling lives in `scripts/` next to this skill (`.claude/skills/devloop/scripts/`) — self-contained, it
drives the deployed app + live API, not local source. Read its `README.md` once if unsure.

The client React source it reviews is a **separate repo** sitting next to this one (same parent dir as
`onboarding-service`). To locate a component, resolve that repo first:
`D=$(cd "$(git rev-parse --show-toplevel)/.." && echo "$PWD/onboarding-client")` then search `$D/src`.
Cite fixes as `onboarding-client/src/components/...`. The real repo is the sibling directory named
**exactly** `onboarding-client` that contains a `src/components/` tree — ignore decoys like
`onboarding-client2nope`, `onboarding-client.zip`, etc. (don't rely on the remote URL or package name;
several decoys share them). If `$D/src/components` doesn't exist, you've got the wrong dir.

## Safety contract (non-negotiable)

- **Read-only.** Never sign, submit, pay, cancel, or create anything. `tuleva-q.mjs` is GET-allowlisted;
  `drive-live.mjs` only navigates + screenshots. Do not script clicks on sign/confirm/submit. If the
  feature genuinely needs a post-signature state, **stop and hand the engineer the exact action to run.**
- **Real personal + financial data.** Screenshots land under `tmp/live-review/` (gitignored). Never paste
  them, or raw API responses, into PRs/issues/commits without the engineer redacting first.

## Phase 1 — Ensure a live session

First check whether a valid token is already cached:

```sh
cd .claude/skills/devloop/scripts
node tuleva-login.mjs            # --env live is the default
```

- If it prints `✅ already logged in` or `✅ refreshed`, continue.
- If it prints a **verification code**, surface it prominently to the engineer:
  `➡️ Approve code NNNN in your Smart-ID / Mobile-ID app.` The script blocks ~60s polling — wait for it.
- The personal code is read from the skill's `scripts/.env` (`TULEVA_ID_CODE`) automatically — don't stop
  to ask for it. Only if login actually errors with "Missing personal code" does it need setting (in
  `scripts/.env`, or `export TULEVA_ID_CODE=…`; add `--method mobile-id --phone +372…` for Mobile-ID).

## Phase 2 — Capture the feature

Pick the preset that matches the feature, or pass explicit routes. For the default dashboard review:

```sh
node drive-live.mjs --feature dashboard
# other presets: withdrawals | second-pillar | capital
# or explicit:    node drive-live.mjs --route /account --route /2nd-pillar-transactions
```

It screenshots each route at **mobile (375)** and **desktop (1280)**, full-page, into
`tmp/live-review/<slug>-<timestamp>/`, and prints a manifest of absolute paths. Headed by default so the
engineer can watch; add `--headless` if they prefer.

Alongside each `NN-<route>-<viewport>.png` it writes `NN-<route>-<viewport>.elements.json` — a map of the
visible elements (tag, role, testid, text, and full-page pixel `box`) at coordinates that line up 1:1 with
the PNG. You'll use these boxes in Phase 4 to mark findings precisely instead of eyeballing pixels.

If `drive-live.mjs` reports no token, loop back to Phase 1.

## Phase 3 — Pull the matching API data

For each screen, fetch the data the UI claims to show, to fact-check it (not just eyeball it):

```sh
node tuleva-q.mjs /v1/me
node tuleva-q.mjs /v1/pension-account-statement
node tuleva-q.mjs /v1/second-pillar-assets
node tuleva-q.mjs /v1/me/conversion
```

Choose endpoints relevant to the feature (the allowlist in `tuleva-q.mjs` is the menu).

## Phase 4 — Review and mark

**Read every screenshot** from the manifest (open them — you can see images). Then synthesize:

1. **Does the feature work / look right?** Layout, spacing, overflow, truncation, broken images, loading
   or error states stuck on screen, copy/i18n correctness. Call out mobile vs desktop differences.
2. **Does the data match?** Compare on-screen figures (balances, returns, dates, names) against the
   Phase-3 API responses. Flag any mismatch — that's a real bug, not a nitpick.
3. **Regressions / anomalies** visible in the live render.

Then **mark each finding inside the image** so it's unambiguous where it is:

- For each finding, open the screenshot's `.elements.json` and pick the element whose `text`/`box` matches
  what you're flagging — that gives you exact pixel coords. If nothing matches (e.g. a gap/spacing issue),
  estimate a `box` from the image.
- Build a findings array per screenshot: `[{ "n": 1, "severity": "blocking"|"should-fix"|"polish",
  "box": {x,y,width,height}, "note": "<short>" }, …]` — number findings sequentially.
- Annotate:
  ```sh
  node annotate.mjs --image <NN-route-viewport.png> --findings '<the JSON array>'
  ```
  This writes `NN-route-viewport.annotated.png` with numbered colored boxes (red=blocking, amber=should-fix,
  blue=polish) and a legend.
- **Read the `.annotated.png` back** to confirm every box landed on the right element. If a box is off,
  fix its coords and re-run `annotate.mjs`. Only annotated images you've verified count as evidence.

Group findings as **Blocking / Should-fix / Polish**. Be specific and honest — radical candor.

## Phase 5 — Report, output observations, propose

1. **Write `review.md` into the capture dir** — embed the annotated images (relative links), the Phase-3
   data cross-check table, and findings grouped by severity. This is the shareable artifact (PII caveat
   from the safety contract still applies).
2. **Output the observations to the console** (your reply to the engineer) as a numbered list. Every
   observation MUST reference its evidence: the **annotated file** and the **badge number**, the element/box,
   and the proposed fix. One line each, e.g.:
   ```
   [1] blocking · 02-account-desktop.annotated.png #1 · header "Tere, …" (box 40,120 300×32)
       overlaps the logo on desktop → onboarding-client/src/components/account/Header.tsx — add top margin
   ```
3. **Propose concrete fixes, not prose.** Resolve the sibling `onboarding-client` repo (see the locate
   recipe near the top), find the component for each finding, and draft the actual change (file + the diff).

Then **decide**:
- **All good** → one-paragraph summary + the capture dir + `review.md` path, end.
- **Issues found** → the proposals above. If the engineer iterates on the code, they fix, redeploy (or run
  a local build with `--env local`), and you re-run from Phase 2 for a before/after.

Do not loop forever. After two review passes with the feature still broken, surface the blocker and ask
for direction.

## Output format

```
✅ /devloop: <feature>
   - Captures: tmp/live-review/<slug>-<timestamp>/ (N screenshots + annotated + review.md)
   - Data check: <UI matches API | mismatch: …>
   - Observations (each → annotated file #badge):
       [1] <severity> · <file>#<n> · <one-line> → <component> <fix>
       [2] …
   - Findings: <Blocking n · Should-fix n · Polish n>
```
