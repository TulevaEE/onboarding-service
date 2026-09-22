# CLAUDE.md

## Guiding Principles

Follow **Uncle Bob** (Clean Code, SOLID), **Kent Beck** (TDD, simple design), **Martin Fowler** (refactoring, enterprise patterns), and **Lean/Agile** (MVP, iterate, deliver incrementally).

- **"Make the change easy (warning: this is hard), then make the easy change."** — Refactor before significant changes.
- **"As tests get more specific, the code gets more general."**
- **Four Rules of Simple Design**: passes tests → reveals intention → no duplication → fewest elements.
- **Refactor rigorously** — never create backwards-compatible shims, wrappers, or deprecation layers. Change the code directly and update all callers.
- **Split into shippable milestones**: first refactor (no behavior change) → ship. Then make the behavior change → ship. Never mix refactoring and behavior changes in the same step.
- **KISS / YAGNI** — simplest solution that works. Don't build for hypothetical future requirements: "duplication is far cheaper than the wrong abstraction."
- **Boy Scout Rule** — always leave code cleaner than you found it.
- **DORA four key metrics** — optimize for deployment frequency, lead time for changes, change failure rate, and time to restore.
- **Decision-first, not integration-first** — when adding external API integrations, start from "which business decision does this unblock?" not "which endpoints can we call?" Only add a new operation when a failing test proves the current data cannot answer a required decision.
- **Radical candor** — care personally, challenge directly. Say what you actually think: push back on bad ideas, name risks, disagree with reasoning. Don't hedge to be polite or agree to avoid friction. Ruinous empathy (soft to spare feelings) and manipulative insincerity (vague to avoid conflict) both waste the user's time.
- **Interview before planning** — ask follow-up questions before producing a plan. Surface ambiguity, missing constraints, hidden assumptions, and success criteria up front. A plan built on guessed requirements is worse than no plan. If the request is non-trivial, ask first; only skip the interview when the task is genuinely unambiguous.

## TDD-First Planning

**Structure plans as TDD steps — not production code changes.**

1. **Failing integration test** — what it asserts and why it fails
2. **For each unit of work**: failing unit test → minimal production code → green → refactor
3. **Integration test turns green**
4. **Full test suite — no regressions**

❌ "1. Add migration 2. Add enum 3. Add method 4. Write tests"
✅ "1. Failing integration test 2. Failing unit test for A → implement A → refactor 3. Integration test passes 4. Full suite green"

**Bug fixes**: don't just reproduce the specific bug — ask what tests are missing that would catch this and similar bugs.

**External integrations**: the failing integration test should assert a *business outcome* (e.g., "active board members for registry code X"), not infrastructure plumbing (e.g., "XSD generates classes"). Infrastructure setup is a prerequisite, not a test target.

## Commands

### Development
- `./gradlew bootRun` (port 9000, requires PostgreSQL)
- `./gradlew bootRun --args='--spring.profiles.active=dev,mock'` (mock EpisService)
- `docker compose up database -d` (local PostgreSQL)
- `./gradlew build` | `./gradlew build -x test`
- Spring profile `dev` for local development; do NOT use `--no-daemon`

### Testing
- `./gradlew test` (H2) | `SPRING_PROFILES_ACTIVE=pg,test ./gradlew test` (Testcontainers PostgreSQL, requires Docker)
- `./gradlew test --tests MyTest` (integration tests live under `src/test/groovy/` alongside unit tests and run as part of `test` — there's no separate `integrationTest` task)
- `./gradlew jacocoTestReport`
- Some tests require PostgreSQL and are skipped on H2

### Code Quality
- `./gradlew spotlessApply` (Google Java style via Spotless)
- `./gradlew spotlessCheck`

### Refactoring Metrics (the oracle)
- `./gradlew scorecard` — runs tests + PMD, aggregates everything into `metrics/scorecard.json`, and **fails on any ratchet regression** vs the previous commit's scorecard. Ratcheted down: modulith violations, module cycles, PMD violations, unmarked packages, @SpringBootTest count, top-15 class lines. Ratcheted up: line/branch coverage.
- `ModuleMetricsTest` emits `build/metrics/modulith.json` (violations, cycles, per-module instability) and ratchets against the committed `metrics/baseline.json` — lower the baseline numbers as you fix boundaries, never raise them.
- `./gradlew pitest -Ppitest.target='ee.tuleva.onboarding.some.package.*'` — mutation testing (PIT + Spock/JUnit). Scope to changed classes per iteration; full runs are for trend data only (incremental scores and full-run scores are not comparable).
- `scripts/pitest-slice.sh <module>` — runs pitest for one top-level module and records it in `metrics/pitest-slices.json` (per-module mutations/detected/score/date). Mutation scores decompose by class, so the mutation-weighted aggregate over complete slices equals a full run; the scorecard reports it as `mutationScoreSliced`/`mutationsSliced`/`pitestSlicedModules`. The loop runs one slice per iteration as a rotation; once every module has a slice (now the case), the aggregate IS the headline `mutationScore`; the rotation keeps slices fresh module by module.
- PMD complexity/cohesion thresholds live in `config/pmd/ruleset.xml`; violation counts feed the scorecard, thresholds stay fixed.
- After improving a metric, regenerate and commit `metrics/scorecard.json` (and lower `metrics/baseline.json`) in the same commit as the improvement.

### Git
- Always `git add` new files immediately
- NEVER commit or push without user approval
- **NEVER commit PII — this repo is open-source.** No real names, personal/ID codes, emails, phone numbers, addresses, account/väärtpaberikonto numbers, or other personally identifiable data in code, comments, tests, fixtures, migrations, or commit messages. Use synthetic values (e.g. `38888888888`) or stable opaque IDs (payment id, `external_id`, party UUID) instead. This also applies to logs and anything pushed to an external service.
- **`.githooks/pii-scan` enforces the rule** on added lines, at three points: `pre-commit`, `pre-push`, and the CircleCI `pii-scan` job. The push hook is the load-bearing one — on a public repo a push *is* publication, so CI can only ever report what is already visible. Run `./gradlew setupGitHooks` once per clone to enable the hooks.
  - **It is the only thing in either hook that blocks.** Spotless and migration-order run beside it and merely warn, because git's `--no-verify` covers a whole hook rather than one check: anything else that can stop a commit or a push is also a reason to switch the scan off, and both of those are caught later by CI. Personal data is not — once pushed it is published.
  - Detects ID codes (mod-11), IBANs (mod-97), `first.last@` emails, and names on a roster. Checksums keep it quiet: `38888888888` and timestamps do not match.
  - Values that do pass their checksum and have to appear anyway are exempted via cleartext allowlists (`.githooks/pii-idcode-allow`, `.githooks/pii-iban-allow`) — only published demo/test identities, published company accounts, or repo-invented fixtures long public here; never a real person's data.
  - Names cannot be recognised by pattern, so `.githooks/pii-roster.sha256` holds *salted hashes* — the roster guards against personal data without containing any. Add a colleague with `.githooks/pii-roster-add 'Eesnimi Perekonnanimi'`.

## Architecture

Spring Boot application following DDD and Spring Modulith best practices. Java 25 with preview features. Controllers are thin routing layers — extract complex logic into `@Component` classes (`*Verifier`, `*Mapper`, `*Validator`).

**Spring Modulith** — each top-level package under `ee.tuleva.onboarding` is a module. Only types in the package root are public API; sub-packages are internal. Modules communicate via Spring application events, not direct cross-module injection. Use `@ApplicationModuleTest` to verify module boundaries.

**Anti-corruption layer for external APIs** — external API response types (JAXB, JSON DTOs) must not leak outside their module. Map to domain records at the module boundary. Use stable identifiers (codes, enums) over localized text for domain rules. External API modules expose only domain types as their public API.

**EPIS consent boundary** — a personalized EPIS (Pensionikeskus) query is any `EpisService` method that takes a `Person` (contact details, account statement, cash flows, applications, contributions, fund pension, second pillar assets) and everything wrapping it (`ContactDetailsService`, `AccountStatementService`, `UserConversionService`, `SecondPillarPaymentRateService`, `ApplicationService`, `ThirdPillarTaxHeadroom`, the `nudge` module's EPIS-backed inputs). The person consents on the login screen, and the consent covers what that person is doing in that session. The test is conceptual, not technical: work that the person set in motion (a payment they just made and are returning from, the Montonio notification for it, a mandate they just signed, an `@Async` listener or the batch poller finishing their action) is still the same logged-in person, even when the backend request carries no JWT, so minting a context for them there with `SecurityContextRunner.runAs` / `callAs` is fine. What is not allowed is querying EPIS for people who are not acting: `@Scheduled` jobs, bulk audiences, reminders, analytics, or anything that runs days later. Those must use data we already hold: the `unit_owner` registry snapshot (a bulk export), analytics tables, our own database, or facts stored during a session. If a job needs a fact only EPIS has, store it while the person is logged in.

### Transaction registry

Post-send order lifecycle (was a Google Sheet, now Java/Postgres). Pipeline: SEB pushes `seb/yyyy-MM-dd_pending_transactions.csv` to S3 → `ReportImportJob` parses to `investment_report.raw_data` (provider=SEB, reportType=PENDING_TRANSACTIONS) → `SebPendingTransactionReconciliationService` matches `Client ref` → `TransactionOrder.orderUuid` (M3 falls back to fund/ISIN/side/quantity when uuid missing) → writes `TransactionExecution` (1:1 with order, table `investment_transaction_execution`) and transitions `OrderStatus` to `EXECUTED`.

Package layout: `ee.tuleva.onboarding.investment.transaction.ingest/` holds reconciliation, matchers, NAV cross-check, and alert listeners; `ee.tuleva.onboarding.investment.transaction.portfolio/` holds the cost-basis ledger (`investment_portfolio_cost_basis`). Module public API is just `TransactionExecution`, `TransactionExecutionRepository`, `PortfolioCostBasisService.snapshotForFundAndDate(TulevaFund, LocalDate)`, and the `ExecutedPriceSource` interface with its `ExecutedPrice` record — everything else is package-private. `TransactionModuleBoundaryTest` enforces this with ArchUnit: nothing outside `investment.transaction..` may reference `ingest/`, `portfolio/`, `calculation/` or `export/`, so a new cross-module need is published as an interface in the **package root** with the implementation left inside the sub-package (`ingest.SebExecutedPriceSource` reads the pending-transactions report the NAV flow check needs, and the register cannot answer that while orders are still created outside this service).

Scheduled jobs: `SebPendingTransactionReconciliationJob` (daily 09:00 EET, 7-day lookback) is load-bearing because `ReportImportCompleted` only fires on a *successful* `saveReport` — unchanged S3 files produce no event. `PortfolioCostBasisJob` (daily 10:00 EET) advances the ledger; a self-heal job (02:30 EET) rebuilds the last 14 days.

SEB reports quantities and prices to **10 significant digits**, so a fund holding above 10M units comes back with its third decimal rounded off (`18811874.096` → `18811874.1`). The reported quantity is therefore not the authority on units redeemed — `ReportedQuantityNormalizer` takes the ordered quantity whenever the cumulative agrees at that precision, with the closing piece absorbing the residue. Without it a full redemption reads as an overfill, is quarantined before the upsert and never settles; stored raw it would also leave a negative residual in the cost-basis ledger.

NAV cross-check: SEB execution `unit_price` vs `nav_report.market_price` for same ISIN+date, ETF-only, T+0, `1.0%` tolerance. Alerts (unmatched rows, price mismatches, overdue settlements) go through `EmailService.sendSystemEmail` (Mandrill).

## Database

### Migrations
Flyway in `src/main/resources/db/migration/`. H2 compat migrations: `V1_{n-1}_1__.sql`.

- **Strict version ordering everywhere — never enable `out-of-order`.** A migration numbered below an already-deployed version fails validation and blocks all deploys (ECS rolls back silently while CI stays green). Before merging, renumber your migrations above the current master max — and re-check after every merge to master, since a racing PR may have claimed your numbers (this happened with V1_198–V1_202: the PR that renumbered *to* V1_202 deployed first and stranded V1_198–V1_201).
- **`.githooks/migration-order` enforces this** at `pre-push` and in the CircleCI `migration-order` job. CI is the load-bearing one: the race happens *after* you push, when a PR that merges first takes your numbers, and only a re-run against the new master can see it. At `pre-push` it therefore warns rather than blocks: git's single `--no-verify` covers the whole hook, so a numbering complaint that stopped a push would also be a reason to switch off the `pii-scan` standing beside it, which is the one check there that must block. It rejects a version at or below master's max and a version claimed twice, reading `src/main/resources/db/migration` and `src/main/java/db/migration` as the one sequence Flyway applies them as — listing only the SQL directory is how `V1_275` came to be held by a migration in each. A gap above master's max only warns: Flyway applies `1.278` and `1.280` in order whether or not `1.279` exists, and failing on it would stop two open PRs from holding distinct numbers, which is the normal case. H2 compat migrations are compared on the full version, so `V1_270_1` beside master's `V1_270` is accepted. `.githooks/migration-order-test` self-tests the checker.
- **Explicit constraint names always** — H2 and PostgreSQL generate different auto-names
- **Recreate tables** for complex schema changes (create new → migrate → drop old → rename)
- **Standard SQL only** — must work on both H2 and PostgreSQL:
  ```sql
  -- ❌ ALTER INDEX old_name RENAME TO new_name;  (PostgreSQL-only)
  -- ✅ ALTER TABLE t RENAME CONSTRAINT old_name TO new_name;
  ```

### PostgreSQL Types
Prefer: `text` over `varchar` unless the length is a domain invariant (e.g., `varchar(3)` for ISO 4217 currency codes, `varchar(2)` for ISO country codes). Use `varchar(n)` when the spec guarantees the max length — use `text` when the limit would be arbitrary. Other preferences: `timestamptz` (pairs with `Instant`), `bigint` for IDs, `numeric(19,2)` for money, `uuid` for external IDs, `jsonb` (not `json`), `boolean` (not int flags). Always index foreign keys.

### Preferences
- Always use the latest Spring classes (`JdbcClient`, `RestClient`, etc.) over legacy equivalents
- **Inject `Clock`** — never call `Instant.now()` / `LocalDate.now()` directly
- **No `entityManager.flush()` in production code** — fix the underlying issue instead

## Testing

### Strict TDD

**Never write production code without a failing test. Execute sequentially — never batch.**

**Macro (Integration):** failing integration test → micro TDD cycles → integration test green → full suite green.

**Micro (Unit):** RED (failing test) → GREEN (minimal code) → REFACTOR. Run tests after every change.

### Conventions
- **JUnit 5** default; **Spock** for data tables with `@Unroll`
- All test files under `src/test/groovy/` (including Java tests)
- Naming: `*Test.java` / `*Spec.groovy`. Descriptive method names, no `@DisplayName`, no Spock label strings
- Groovy BigDecimal: `10500.00` not `new BigDecimal("10500.00")`
- Coverage floors for AML and deadline packages are enforced via `jacocoTestCoverageVerification` (wired into `check`); floors are set at measured reality and only ratcheted up, back toward 100%
- **AssertJ only**. Compare full objects/collections. Never assert on exception/log messages
- Only mock injected dependencies. Never mock data classes. Prefer real instances and test fixtures
- **BDDMockito** — prefer `given`/`willReturn` over `when`/`thenReturn` for readability
- Avoid `ArgumentCaptor` — assert on return values or `verify` with expected object
- **Prefer test slices** over `@SpringBootTest` — use `@WebMvcTest` for controllers, `@DataJpaTest` for repositories, etc. Only use `@SpringBootTest` when a slice won't cover the integration
- Controller tests: `@WebMvcTest` + `@WithMockUser` + `@MockitoBean` (not `@MockBean`) + `.with(csrf())` + `@TestPropertySource` (not `ReflectionTestUtils`)

### Checks and alerts

A check that cannot fire is worse than no check: the digest reports it as verified, so nobody looks again. Before a new check ships, both of these hold.

- **Drive it from real rows, not from stubs.** A unit test that stubs the repository proves a branch exists, not that the query can reach it. Every check reading the database gets an `*IT` that inserts real rows and asserts the severity that comes back. Two shapes have both shipped here: a filter on a column the writing path never populates matches nothing, so the check's input stays `0` while every test passes; and a comparison between two aggregations over the same column is bounded by the rounding they differ in, so it can never exceed its own tolerance. A unit test reaches a branch like that only by stubbing a value the query cannot produce — proving the branch exists rather than that anything can reach it.
- **Name what fires it and what clears it, in the test names.** A finding with no path back to PASS is a permanent line in the digest. A check that runs for a fund it can never observe returns the same non-answer every period, in a state that can never clear — that is a static fact about the system, not a periodic finding, so return no finding at all.
- **Ops noise is a defect, not a cosmetic issue.** Operators learn to skim a message that is mostly boilerplate, and the month it finally carries a real FAIL, nobody reads it.
- **One entity's failure must not take the others down**, and a check that could not run says so rather than returning nothing — an empty result reads as "nothing wrong".

## Code Style

### Project-Specific Conventions
- **Log/exception format**: `"Description: param1=value1, param2=value2"` — greppable
- **No comments, no Javadoc** on implementation classes. Extract well-named methods instead. This binds hardest on *rationale* — the regulation behind a threshold, why a window opens where it does, why a branch is safe. The rationale is not deleted, it moves into the identifier: `SISEKORD_4_P_11_7_FIRST_ESCALATING_BREACH_DAY`, not a comment citing the rule above `ESCALATION_THRESHOLD_FALLBACK`. Applies to SQL and migrations too. Test names carry the narrative; a test needing a comment to say what it pins is misnamed
- **No unused parameters.** An `@EventListener` that ignores its event names the type in the annotation instead: `@EventListener(RunLimitCheckRequested.class) void onLimitCheckRequested()`. The only exceptions are signatures a framework fixes — `RowMapper`'s `rowNum`, a parameter a SpEL cache key references (`key = "#person.representedPersonalCode"`), an interface being implemented. This is the most frequent automated review finding on our PRs: resolve it before merge rather than leaving the comment standing
- **Static imports**: assertions, constants, collectors, enum values
- **Immutability**: `final` fields and public API params; NOT local variables. Prefer `List.of()`, `Map.of()`, records, `@Builder`+`@Singular`, `@Value`
- **Streams** over for-loops. Method references over lambdas when clearer
- **Method overloading** instead of passing null
- **Null safety**: opt in per package with `@NullMarked` in `package-info.java` (jspecify). NullAway runs at `ERROR` on every `@NullMarked` package and blocks the build. In a marked package, every reference is `@NonNull` by default — annotate genuinely-nullable fields/params/returns with `org.jspecify.annotations.@Nullable`. New packages should be `@NullMarked` from the start; when touching an unmarked file, mark its package (or just the class) and fix the resulting NullAway errors in the same change rather than leaving the lie. Tests bypass NullAway, but specs/tests in `src/test/groovy/` go through `compileTestGroovy` which is out of scope anyway.
- **Never substitute empty strings (or sentinels) for missing data**: never return `""` (or `0` / `-1` / other sentinels) in place of an absent value. Fail fast — throw on a missing required field, or model the absence with `@Nullable`. Empty-string defaults create silent failures that propagate through the system instead of surfacing at the source; a NullPointerException or an explicit exception is preferable to a phantom empty value.
- **Law of Demeter**: push behavior to where the data lives (`account.isUserAccount()` not `entry.getAccount().getPurpose() == USER_ACCOUNT`)
- **Retries on external calls**: use Spring Framework 7 native resilience (`org.springframework.core.retry.RetryTemplate` + `RetryPolicy.builder()` with `includes`/`excludes`), NOT the legacy `spring-retry` lib. Wrap the integration-boundary call and pair with a deterministic idempotency key. Reference: `SebGatewayClient.submitPaymentFile` + `SebGatewayConfiguration.sebGatewayRetryTemplate` + `SebGatewayClientRetryTest`.
