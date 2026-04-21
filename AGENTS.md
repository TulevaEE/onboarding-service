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
- `./gradlew test --tests MyTest` | `./gradlew integrationTest`
- `./gradlew jacocoTestReport`
- Some tests require PostgreSQL and are skipped on H2

### Code Quality
- `./gradlew spotlessApply` (Google Java style via Spotless)
- `./gradlew spotlessCheck`

### Git
- Always `git add` new files immediately
- NEVER commit or push without user approval

## Architecture

Spring Boot application following DDD and Spring Modulith best practices. Java 25 with preview features. Controllers are thin routing layers — extract complex logic into `@Component` classes (`*Verifier`, `*Mapper`, `*Validator`).

**Spring Modulith** — each top-level package under `ee.tuleva.onboarding` is a module. Only types in the package root are public API; sub-packages are internal. Modules communicate via Spring application events, not direct cross-module injection. Use `@ApplicationModuleTest` to verify module boundaries.

**Anti-corruption layer for external APIs** — external API response types (JAXB, JSON DTOs) must not leak outside their module. Map to domain records at the module boundary. Use stable identifiers (codes, enums) over localized text for domain rules. External API modules expose only domain types as their public API.

## Database

### Migrations
Flyway in `src/main/resources/db/migration/`. H2 compat migrations: `V1_{n-1}_1__.sql`.

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
- 100% coverage enforced for AML and deadline packages
- **AssertJ only**. Compare full objects/collections. Never assert on exception/log messages
- Only mock injected dependencies. Never mock data classes. Prefer real instances and test fixtures
- **BDDMockito** — prefer `given`/`willReturn` over `when`/`thenReturn` for readability
- Avoid `ArgumentCaptor` — assert on return values or `verify` with expected object
- **Prefer test slices** over `@SpringBootTest` — use `@WebMvcTest` for controllers, `@DataJpaTest` for repositories, etc. Only use `@SpringBootTest` when a slice won't cover the integration
- Controller tests: `@WebMvcTest` + `@WithMockUser` + `@MockitoBean` (not `@MockBean`) + `.with(csrf())` + `@TestPropertySource` (not `ReflectionTestUtils`)

## Code Style

### Project-Specific Conventions
- **Log/exception format**: `"Description: param1=value1, param2=value2"` — greppable
- **No comments, no Javadoc** on implementation classes. Extract well-named methods instead
- **Static imports**: assertions, constants, collectors, enum values
- **Immutability**: `final` fields and public API params; NOT local variables. Prefer `List.of()`, `Map.of()`, records, `@Builder`+`@Singular`, `@Value`
- **Streams** over for-loops. Method references over lambdas when clearer
- **Method overloading** instead of passing null
- **Law of Demeter**: push behavior to where the data lives (`account.isUserAccount()` not `entry.getAccount().getPurpose() == USER_ACCOUNT`)
- **Retries on external calls**: use Spring Framework 7 native resilience (`org.springframework.core.retry.RetryTemplate` + `RetryPolicy.builder()` with `includes`/`excludes`), NOT the legacy `spring-retry` lib. Wrap the integration-boundary call and pair with a deterministic idempotency key. Reference: `SebGatewayClient.submitPaymentFile` + `SebGatewayConfiguration.sebGatewayRetryTemplate` + `SebGatewayClientRetryTest`.
