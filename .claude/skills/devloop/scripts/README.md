# devloop

Tooling to drive the **LIVE** Tuleva app as the engineer, for visual review.

You log in once with your own Estonian ID code (you approve the Smart-ID / Mobile-ID prompt on your phone), then read live API data and screenshot real features in a real browser.

Everything defaults to **LIVE** (`https://pension.tuleva.ee`). Staging / local are reachable only via `--env staging` / `--env local`.

This package lives next to the skill at `.claude/skills/devloop/scripts/` and is **isolated** — it has its own `package.json` and `node_modules` and is never part of the Gradle build or CI.

## One-time setup

After you pull, the `/devloop` skill is already there — Claude Code auto-discovers it from
`.claude/skills/devloop/`, so any engineer can just say *"use the devloop skill to review the dashboard
on live"*. Each engineer only needs this one-time, per-machine setup with **their own** ID code:

```sh
cd .claude/skills/devloop/scripts
npm install
npx playwright install chromium
cp .env.sample .env         # then set TULEVA_ID_CODE=<your Estonian personal code>
```

The scripts auto-load `.env` from this directory (gitignored). Put `TULEVA_ID_CODE` there once and you
never pass `--code`. A shell `export TULEVA_ID_CODE=…` still works and takes precedence.

`.env` is personal and never committed — everyone uses their own code and approves the login on their own
phone. The first `tuleva-login.mjs` (or first `/devloop` run) does the Smart-ID/Mobile-ID approval; the
token caches to `~/.tuleva/`, so later runs need no phone until it expires.

## Commands

### 1. Log in (`tuleva-login.mjs`)

Authenticates and caches the token at `~/.tuleva/<env>-token.json` (mode 0600). Reuses a valid cached token, refreshes an expired one, and only does a full login (with phone approval) when needed.

```sh
node tuleva-login.mjs                       # live, smart-id, code from TULEVA_ID_CODE
node tuleva-login.mjs --method mobile-id --phone +37200000000
node tuleva-login.mjs --code 38001010000
node tuleva-login.mjs --force               # force re-login
node tuleva-login.mjs --env staging
```

It prints a verification code — approve that code in your Smart-ID / Mobile-ID app.

### 2. Read API data (`tuleva-q.mjs`)

Read-only GET client. Refuses anything outside a fixed allowlist; never writes.

```sh
node tuleva-q.mjs /v1/me
node tuleva-q.mjs /v1/me/capital
node tuleva-q.mjs /v1/pension-account-statement
node tuleva-q.mjs --env staging /v1/funds
```

Allowed paths: `/v1/me`, `/v1/me/principal`, `/v1/me/capital`, `/v1/me/capital/events`, `/v1/pension-account-statement`, `/v1/savings-account-statement`, `/v1/second-pillar-assets`, `/v1/contributions`, `/v1/me/conversion`, `/v1/withdrawals/eligibility`, `/v1/withdrawals/fund-pension-status`, `/v1/applications`, `/v1/funds`, `/v1/funds/<isin>/nav`, `/v1/listings`.

### 3. Screenshot features (`drive-live.mjs`)

Launches Chromium (headed by default), boots the SPA authenticated, and screenshots each route at mobile + desktop viewports. **It only navigates and screenshots — never clicks, fills, or submits.**

```sh
node drive-live.mjs --feature dashboard
node drive-live.mjs --feature withdrawals
node drive-live.mjs --feature second-pillar
node drive-live.mjs --feature capital
node drive-live.mjs --route /account --route /capital
node drive-live.mjs --feature dashboard --headless
```

Feature presets:

- `dashboard` → `/account`
- `withdrawals` → `/withdrawals`
- `second-pillar` → `/account`, `/2nd-pillar-transactions`, `/2nd-pillar-contributions`
- `capital` → `/capital`

Screenshots land in `tmp/live-review/<feature>-<timestamp>/` (under the onboarding-service repo root). The script prints the output dir and every screenshot path on its own line. Next to each `NN-route-viewport.png` it also writes `NN-route-viewport.elements.json` — the visible elements (tag, role, testid, text, full-page pixel `box`) at coordinates that line up 1:1 with the PNG, so findings can be marked precisely.

### 4. Annotate findings (`annotate.mjs`)

Draws numbered, colored boxes + a legend onto a screenshot to mark findings in place. Coordinates come from the `.elements.json` (or eyeballed). Reuses the bundled Chromium — no extra deps.

```sh
node annotate.mjs --image tmp/live-review/<dir>/02-account-desktop.png \
  --findings '[{"n":1,"severity":"polish","box":{"x":170,"y":770,"width":940,"height":700},"note":"returns section reads alarming"}]'
```

`--findings` is a JSON array (inline or a file path) of `{n, severity, box:{x,y,width,height}, note}`. Severity colors: `blocking`=red, `should-fix`=amber, `polish`=blue. Writes `<image>.annotated.png` (override with `--out`).

## Safety contract (read this)

- **Read-only / no writes / no signing.** `tuleva-q.mjs` enforces a GET allowlist; `drive-live.mjs` only navigates and screenshots. There is no code path that clicks, fills, submits, or signs a mandate/payment.
- **Live data.** Screenshots and API responses contain **real personal and financial data**. Output lives under `tmp/` and is gitignored. Never paste screenshots or API output into PRs, issues, or chats **unredacted**.
- **Tokens are never printed.** The access/refresh tokens are written only to `~/.tuleva/<env>-token.json` (mode 0600) and never echoed.
- **Default is LIVE.** Use `--env staging` or `--env local` to target anything else.
