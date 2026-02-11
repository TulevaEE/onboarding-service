# AGENTS.md

This file provides guidance to AI coding agents when working with code in this repository.

## Commands

### Development
- **Run locally**: `./gradlew bootRun` (runs on port 9000, requires PostgreSQL)
- **Run with profiles**: `./gradlew bootRun --args='--spring.profiles.active=dev,mock'`
- **Database**: `docker compose up database -d` (starts PostgreSQL locally)
- **Build**: `./gradlew build`
- **Build without tests**: `./gradlew build -x test`
- **Clean build**: `./gradlew clean build`

### Testing
- **Run all tests**: `./gradlew test` (runs against H2 in-memory database)
- **Run tests with PostgreSQL**: `SPRING_PROFILES_ACTIVE=ci,test ./gradlew test` (uses Testcontainers PostgreSQL)
- **Run specific test class**: `./gradlew test --tests MyTest`
- **Run with test output**: `./gradlew test --info`
- **Code coverage report**: `./gradlew jacocoTestReport` (report in `build/reports/jacoco/test/html/index.html`)
- **Integration tests**: `./gradlew integrationTest`

#### Test Database Profiles
- **Default (no profile or without 'ci')**: Tests run against H2 in-memory database
- **With 'ci' profile**: Tests run against PostgreSQL via Testcontainers (e.g., `SPRING_PROFILES_ACTIVE=ci,test`)
  - **Requires Docker to be running** for Testcontainers to work
- **CI environment**: Tests automatically use PostgreSQL when `CI=true` environment variable is set
- Some tests require PostgreSQL-specific features (jsonb, advanced queries) and will be skipped when running against H2

### Code Quality
- **Apply formatting**: `./gradlew spotlessApply`
- **Check formatting**: `./gradlew spotlessCheck`
- **Check dependencies**: `./gradlew dependencyCheckAnalyze`

### Git Operations
- **ALWAYS add new files to git**: After creating any new file, immediately run `git add <filepath>`
- **Check git status regularly**: Run `git status` to ensure all new files are tracked
- **Add all new files**: `git add .` (be careful with this, check what you're adding first)
- **Add specific file**: `git add src/main/java/path/to/NewFile.java`
- **Important**: Untracked files won't be included in commits, always verify new files are added
- **NEVER commit or push without user approval**: Always show the changes and wait for explicit user confirmation before committing. Never push to remote without the user explicitly asking for it.

### Database
- **Run migrations**: Automatic with `dev` profile
- **Generate migration**: Create file in `src/main/resources/db/migration/` following naming convention

### Important Notes
- Always set Spring active profile to `dev` for local development
- Use `mock` profile to mock EpisService if you don't want to run epis-service
- The application uses Java 25 with preview features enabled
- Code formatting with Google Java style is enforced via Spotless
- High test coverage requirements are enforced for AML and deadline packages (100% class/method/line coverage)
- **DO NOT use `--no-daemon` flag** when running Gradle commands locally - the daemon significantly improves build performance and should only be disabled in CI/CD environments

### Monitoring & Observability
- **Exception tracking**: Sentry integration for production error monitoring
- **Health checks**: Available at `/actuator/health`

## Architecture

This is Tuleva's pension management onboarding service - a Spring Boot application that handles Tuleva Estonian pension fund management.

The name has historical reasons, it does more than onboarding these days.

### Core Domain Structure
The application follows domain-driven design with these main domains:

 #### Authentication & Users
- **auth**: OAuth2 authentication with Mobile-ID, Smart-ID, and ID-card
- **user**: User management and personal information
- **member**: Tuleva member registration and management
- **signature**: Digital signing with Estonian e-ID infrastructure

#### Financial Operations
- **account**: Account statements and transaction history
- **capital**: Capital transfer contracts and management
- **contribution**: Contribution tracking and management
- **payment**: Payment processing and reconciliation
- **paymentrate**: Payment rate calculations
- **withdrawals**: Pension withdrawal processing
- **savings**: Savings account management

#### Pension Management
- **fund**: Pension fund information and NAV management
- **mandate**: Pension fund switching and mandate management
- **pillar**: Second and third pillar pension management
- **conversion**: Fund conversion operations
- **holdings**: Fund holdings tracking

#### Compliance & Risk
- **aml**: Anti-money laundering checks and compliance
- **audit**: Audit trail and logging
- **deadline**: Deadline management and enforcement

#### Integrations
- **epis**: Estonian Pension Information System integration
- **swedbank**: Banking integration for account fetching and reconciliation
- **notification**: Email notifications via Mandrill/Mailchimp

#### Internal Systems
- **ledger**: Double-entry bookkeeping system for financial transactions
- **event**: Event sourcing and domain events
- **analytics**: Analytics and reporting
- **comparisons**: Fund comparison tools
- **dashboard**: User dashboard data
- **administration**: Admin panel functionality
- **config**: Application configuration management
- **currency**: Currency conversion and management
- **listing**: Various listing and search functionalities
- **locale**: Internationalization and localization
- **time**: Time-related utilities and services
- **error**: Error handling and reporting

### Key Technologies
- **Backend**: Java 25, Spring Boot 3.5.x, Spring Security, JPA/Hibernate
- **Database**: PostgreSQL (production), H2 (tests), Flyway migrations
- **Testing**: Spock framework (Groovy) for unit and integration tests, JUnit 5 also acceptable, Spring Boot Test, MockServer
- **Authentication**: Estonian e-ID (Mobile-ID, Smart-ID, ID-card), OAuth2
- **Documentation**: OpenAPI/Swagger UI
- **Monitoring**: Sentry for exceptions, Micrometer for metrics

### Integration Points
- **EPIS**: Estonian Pension Information System for pension data
- **Swedbank Gateway**: Bank account integration using ISO 20022 XML
- **DigiDoc4j**: Digital signing infrastructure
- **Mandrill/Mailchimp**: Email sending and marketing
- **AWS S3**: File storage and configuration
- **Opensanctions**: AML screening

### Security & Authentication
- OAuth2 implementation supporting Estonian e-ID methods
- Digital certificate validation and trust store management
- Session-based authentication with JDBC session storage
- Anti-money laundering (AML) compliance checks

### Database
- PostgreSQL with Flyway migrations in `src/main/resources/db/migration/`
- H2 compatibility migrations for testing in format `V1_{n-1}_1__.sql`
- Session storage via Spring Session JDBC

#### Migration Best Practices
- **Always use explicit constraint names**: H2 and PostgreSQL generate different auto-names for constraints. Always name constraints explicitly to ensure migrations work on both databases:
  ```sql
  -- ❌ Bad: Auto-generated constraint name (differs between H2 and PostgreSQL)
  CREATE TABLE my_table (
      id bigint primary key
  );

  -- ✅ Good: Explicit constraint name (consistent across databases)
  CREATE TABLE my_table (
      id bigint not null,
      constraint my_table_pkey primary key (id)
  );
  ```
- **Recreate tables for complex schema changes**: When changing primary keys or making complex alterations, create a new table, migrate data, drop old table, and rename - this is more compatible across H2 and PostgreSQL than `ALTER TABLE ... DROP CONSTRAINT`
- **Prefer standard SQL over database-specific syntax**: Migrations must work on both H2 (tests) and PostgreSQL (production). Use ANSI SQL where possible:
  ```sql
  -- ❌ Bad: PostgreSQL-specific (doesn't work in H2)
  ALTER INDEX old_name RENAME TO new_name;

  -- ✅ Good: Standard SQL (works in both H2 and PostgreSQL)
  ALTER TABLE my_table RENAME CONSTRAINT old_name TO new_name;
  ```

#### PostgreSQL Best Practices
- **Prefer `text` over `varchar(n)`**: PostgreSQL treats them identically internally, but `varchar(n)` adds unnecessary length checking and can cause migration issues if you need to increase the length later
- **Use `char(n)` for fixed-length identifiers**: When a field has a known fixed length (e.g., Estonian personal code is always 11 characters), `char(11)` is appropriate and documents the constraint
- **Use `timestamptz` for timestamps**: Always use `timestamp with time zone` (or `timestamptz`) instead of `timestamp`. It stores in UTC internally and handles timezone conversions correctly. Pairs well with Java `Instant`
- **Use `bigint` for IDs**: Prefer `bigint` over `integer` for primary keys to avoid running out of IDs in high-volume tables
- **Use `numeric` for money**: Never use `float` or `double` for monetary values. Use `numeric(precision, scale)` (e.g., `numeric(19,2)` for currency amounts)
- **Add indexes for foreign keys**: PostgreSQL doesn't automatically index foreign key columns. Add indexes explicitly for columns used in JOINs
- **Use `jsonb` over `json`**: When storing JSON data, prefer `jsonb` which is binary and supports indexing, over `json` which stores as text
- **Use `uuid` for external identifiers**: Use native `uuid` type instead of `varchar(36)` for UUIDs - it's more efficient and validates format
- **Prefer `boolean` over integer flags**: Use native `boolean` type instead of `integer` with 0/1 values

#### Database Access
- **Prefer JdbcClient over JdbcTemplate**: Use Spring's modern `JdbcClient` (introduced in Spring Framework 6.1) instead of `NamedParameterJdbcTemplate` or `JdbcTemplate`
  - JdbcClient provides a fluent, chainable API that's more readable and less verbose
  - Better null handling - no need for manual NULL string concatenation
  - Type-safe parameter binding with `.param(name, value)`
  - Simpler exception handling with `.optional()` for single results
  - Example:
    ```java
    // ✅ Good: JdbcClient
    jdbcClient
        .sql("SELECT name FROM users WHERE id = :id")
        .param("id", userId)
        .query(String.class)
        .optional();

    // ❌ Bad: NamedParameterJdbcTemplate
    try {
      String name = jdbcTemplate.queryForObject(sql, params, String.class);
      return Optional.ofNullable(name);
    } catch (EmptyResultDataAccessException e) {
      return Optional.empty();
    }
    ```
  - Use JdbcClient in both production code and tests for consistency
### Time Handling
- **Never use `Instant.now()` or `LocalDate.now()` directly**: Inject `Clock` as a constructor dependency and use `Instant.now(clock)` / `LocalDate.now(clock)` for testability. Prefer constructor-injected `Clock` over static `ClockHolder` — it makes dependencies explicit, avoids hidden global state, and follows standard DI principles

### HTTP Client
- **Prefer RestClient over RestTemplate**: Use Spring's modern `RestClient` (introduced in Spring Framework 6.1) instead of `RestTemplate`
  - RestClient provides a fluent, chainable API similar to WebClient but synchronous
  - More readable and less verbose than RestTemplate
  - Better error handling with built-in status handlers
  - Type-safe request/response handling
  - Consistent API design with other modern Spring components (JdbcClient, WebClient)
  - Example:
    ```java
    // ✅ Good: RestClient
    String result = restClient.get()
        .uri("/api/users/{id}", userId)
        .header("Authorization", "Bearer " + token)
        .retrieve()
        .body(String.class);

    // ❌ Bad: RestTemplate
    HttpHeaders headers = new HttpHeaders();
    headers.set("Authorization", "Bearer " + token);
    HttpEntity<String> entity = new HttpEntity<>(headers);
    ResponseEntity<String> response = restTemplate.exchange(
        "/api/users/{id}",
        HttpMethod.GET,
        entity,
        String.class,
        userId
    );
    String result = response.getBody();
    ```
  - RestClient supports the same features as RestTemplate but with better ergonomics
  - Use RestClient for all new HTTP client code

### Testing Strategy

#### Testing Philosophy: Confidence Over Coverage

**Our Goal**: Write tests that give us confidence that our software works and make refactoring easy.

**Code Coverage Target**: Aim for 80%+ code coverage, but recognize that coverage is not the only metric that matters.

**What Makes a Good Test**:
1. **Gives confidence that the software works** - Tests should verify real-world scenarios and edge cases
2. **Makes refactoring easy** - Tests should not break when implementation details change
3. **Tests behavior, not implementation** - Focus on WHAT the code does, not HOW it does it
4. **Not fragile** - Tests should only break when actual behavior changes, not on every tiny implementation detail change

#### CRITICAL: Always Write and Run Tests
- **Write tests for every change**: Every code modification must have corresponding unit or integration tests
- **Run tests before committing**: Always execute tests to ensure they pass before finalizing changes
- **Fix broken tests immediately**: Never leave failing tests in the codebase
- **Coverage is necessary but not sufficient**: High coverage without good test quality is meaningless
- **Verify against CLAUDE.md principles**: After completing any change, review your code against the principles in this file and fix any violations

#### Strict TDD: Red-Green-Refactor

**This project enforces strict Test-Driven Development. Never write production code without a failing test first.**

The cycle for every change — bug fixes, new features, and refactors:

1. **Red**: Write a failing test that describes the desired behavior. Run it and confirm it fails.
2. **Green**: Write the minimal production code to make the test pass. Nothing more.
3. **Refactor**: Clean up the code while keeping tests green.

Repeat in small increments. Each cycle should be minutes, not hours.

**Rules:**
- Never write production code without a failing test demanding it
- Never write more test code than is sufficient to fail (compilation failures count as failures)
- Never write more production code than is sufficient to pass the currently failing test
- Run tests after every change — both after writing the test (must fail) and after writing the code (must pass)
- If you find yourself writing production code "just to be safe" without a test, stop and write the test first

#### Test Behavior, Not Implementation
- **Always test behavior, not implementation details**: Tests should assert on the output/result, not on how it's achieved
  - ❌ Bad: Verifying mock method calls, testing private methods, asserting on internal state
  - ✅ Good: Asserting on return values, testing through public API, verifying observable behavior
- **Refactor for testability**: If code is hard to test, refactor it to return results instead of only producing side effects
  - Extract pure functions that return values
  - Return result objects instead of just logging
  - Make side effects explicit and minimal
- **Use Stubs over Mocks**: Prefer Stub() for dependencies and assert on results, not interactions
- **Tests should survive refactoring**: If you refactor code without changing behavior, tests should still pass

#### Test Framework
- JUnit 5 tests for integration and unit tests
- Spock framework (Groovy) tests when test data presentable in a table format with @Unroll
- H2 in-memory database for test isolation
- Snapshot testing for complex responses
- MockServer for external service mocking
- High coverage requirements for critical domains (AML, deadlines)
- **Test file naming conventions**: Tests can use either `*Test.java` or `*Spec.groovy` naming patterns:
  - Java/JUnit tests: `MyClassTest.java` (e.g., `UserServiceTest.java`)
  - Groovy/Spock tests: `MyClassSpec.groovy` (e.g., `UserServiceSpec.groovy`)
  - Both naming conventions are equally valid and should be used consistently within each test file
- **Test method names should be descriptive**: Use the test method name itself to describe what it tests (e.g., `findByExternalReference_shouldReturnTransactionsWithMatchingExternalReference()`)
- **Avoid @DisplayName annotations**: The method name should be self-documenting without needing a separate display name
- **Avoid Spock test labels**: Don't use descriptive strings in Spock given/when/then blocks (e.g., `given: "A user exists"`). These are comments that rot over time. The code should be self-documenting:
  - ❌ Bad: `given: "Database has one value"`
  - ✅ Good: `given:` followed by clear, self-documenting code

#### Controller Tests with @WebMvcTest
- **Use `@WebMvcTest` for controller tests**: This loads only the web layer without the full application context
- **Use `@WithMockUser` for security**: Bypasses Spring Security authentication
- **Use `@TestPropertySource` for test properties**: Set configuration values needed by the controller
- **Use `.with(csrf())` for POST/PUT/DELETE requests**: Required when security is enabled
- **Avoid `ReflectionTestUtils`**: Use `@TestPropertySource` to set field values instead of reflection hacks
- **Use `@MockitoBean` (not deprecated `@MockBean`)**: For mocking dependencies in the controller

Example:
```java
@WebMvcTest(MyController.class)
@TestPropertySource(properties = "my.config=value")
@WithMockUser
class MyControllerTest {
  @Autowired private MockMvc mockMvc;
  @MockitoBean private MyService myService;

  @Test
  void myEndpoint_withValidInput_returnsOk() throws Exception {
    mockMvc.perform(post("/my-endpoint")
            .with(csrf())
            .param("input", "value"))
        .andExpect(status().isOk());
  }
}
```

#### Test Location
- **Java tests should be placed in the Groovy test directory**: Even when writing JUnit tests in Java, place them under `src/test/groovy/` alongside Groovy Spock tests
- Follow the same package structure as the main source code

#### Code Style
- **Use static imports extensively** for better readability:
  - Import AssertJ assertions statically (e.g., `import static org.assertj.core.api.Assertions.*`)
  - Import constants and enum values statically
  - Import fixture methods statically
- **Helper methods for readability**: Create helper methods to simplify complex operations (e.g., timezone conversions)
- **Standardized log and exception messages**: Use consistent format for easier grepping:
  - Format: `"Message description: param1=value1, param2=value2"`
  - ✅ Good: `"Bank statement reconciliation failed: bankAccount=DEPOSIT_EUR, closingBalance=1000.00"`
  - ❌ Bad: `"Bank account(DEPOSIT_EUR) closing balance=1000.00 does not match"`
  - This applies to both log statements and exception messages
  - Makes it easy to grep logs for specific parameters or patterns

#### Clean Code Principles
- **Minimal Visibility**: Always use the most restrictive access level possible
  - Make methods `private` by default
  - Use package-private for testing when needed (avoid `public` unless part of the API)
  - Test through public interfaces when possible, but package-private is acceptable for unit testing
  - ❌ Bad: Making everything `public` for convenience
  - ✅ Good: `private` methods, package-private for testing, `public` only for actual API
- **Boy Scout Rule**: Always leave the code cleaner than you found it
  - When touching any class or file, make small improvements
  - Fix formatting issues, remove unnecessary comments, improve variable names
  - Refactor unclear code into well-named methods
  - This creates a continuous improvement flywheel - every change makes the codebase incrementally better
  - Even if you're making a small bug fix, take a moment to clean up the surrounding code
- **Self-documenting code over comments**: Follow clean code principles - code should be self-explanatory
  - Use meaningful variable, method, and class names that clearly express intent
  - Extract complex logic into well-named methods
  - **Avoid comments entirely** - they rot and become outdated; code is the source of truth
- **Boy Scout Rule Examples**:
  - Fixing a bug in a method? Also improve its variable names and extract complex conditions
  - Adding a new field to a class? Remove any unused imports and dead code
  - Updating a test? Remove unnecessary test labels and improve test data setup
  - Modifying a service? Extract magic numbers to constants, improve method names
  - Every PR should include these small improvements alongside the main change
  - **When to use comments** (rare exceptions):
    - External API links: `@see <a href="...">Documentation</a>` for third-party integrations
    - Security-critical WHY: Only when the reason isn't obvious (e.g., "Uses constant-time comparison" → Extract to method `constantTimeEquals()` instead)
    - Legal/licensing requirements
  - **Never comment WHAT the code does** - the code itself should make this obvious
  - **Never comment HOW it works** - extract to well-named methods instead
  - ❌ Bad: `// Check if payment is negative and not to investment account`
  - ✅ Good: Method name `isOutgoingReturnPayment()` with clear boolean logic
  - ❌ Bad: `// Constant-time comparison to prevent timing attacks` + inline code
  - ✅ Good: Extract to method `constantTimeEquals(expected, received)` - name explains intent
  - ❌ Bad: `// Sort parameters by key and concatenate` - obvious from the code
  - ✅ Good: Method name `buildSignedData()` makes intent clear without comments
  - **Remove ALL Javadoc from implementation classes** - method signatures should be self-explanatory
  - **Keep Javadoc only on public APIs** where external documentation is genuinely needed
- **Prefer code readability**: Write code that reads like well-written prose
  - Method names should describe what they do: `hasLedgerEntry()`, `isOnboardingCompleted()`
  - Variable names should describe what they contain: `unrecordedUnattributedPayments`, `closingBankBalance`
  - Avoid abbreviations and cryptic names
- **Avoid single letter variables** (with reasonable exceptions):
  - Use descriptive names for variables with larger scope or complex logic
  - ❌ Bad: `User u = userRepository.findById(id);`
  - ✅ Good: `User user = userRepository.findById(id);`
  - Exceptions where single letters are fine:
    - Loop counters: `for (int i = 0; i < items.size(); i++)`
    - Very short scopes: `catch (Exception e)`
- **Always use imports, never fully qualified class names in code**:
  - ❌ Bad: `var list = new java.util.ArrayList<>();`
  - ✅ Good: `import java.util.ArrayList;` then `var list = new ArrayList<>();`
  - This applies to all types - always add proper imports at the top of the file
- **Variable naming best practices**:
  - **Avoid abbreviations**: Use full, descriptive words
    - ❌ Bad: `dbMap`, `dbValue`, `dbEntry`, `percentageDiff`
    - ✅ Good: `databaseValues`, `databaseValue`, `databaseEntry`, `percentageDifference`
  - **Don't encode type in name**: Variable names should describe content, not type
    - ❌ Bad: `yahooMap`, `databaseMap`, `userList`, `accountArray`
    - ✅ Good: `yahooValues`, `databaseValues`, `users`, `accounts`
  - **Use semantic names**: Names should describe the business meaning
    - ❌ Bad: `value1`, `value2`, `data`, `info`
    - ✅ Good: `databaseValue`, `yahooValue`, `accountBalance`, `userProfile`
  - **Lambda parameter naming**: Use descriptive names in lambdas
    - ❌ Bad: `.filter(fv -> fv.key().equals(fundTicker))`
    - ✅ Good: `.filter(fundValue -> fundValue.key().equals(fundTicker))`
- **Use static imports for readability**:
  - Import commonly used static methods and constants
  - ❌ Bad: `Collectors.toMap()`, `BigDecimal.ZERO`, `RoundingMode.HALF_UP`
  - ✅ Good: `toMap()`, `ZERO`, `HALF_UP` (with appropriate static imports)
  - Common candidates: AssertJ assertions, Stream collectors, Math constants, Enum values
- **Avoid passing null as method parameters**:
  - Use method overloading instead of passing null parameters
  - ❌ Bad: `createTransaction(date, null, metadata, entries)`
  - ✅ Good: Create overloaded method `createTransaction(date, metadata, entries)` that delegates to the full version
  - This makes the API cleaner and prevents NullPointerExceptions
- **Use Lombok for cleaner code**:
  - **@Builder pattern**: For classes with many optional parameters, use @Builder to avoid null constructor arguments
    - ❌ Bad: `new MyEvent("type", "id", 123L, null, null, null)` - unclear which parameters are null
    - ✅ Good: `MyEvent.builder().type("type").id("id").ts(123L).build()` - only set what you need
  - **@Singular for immutable collections**: Use with @Builder to create immutable lists
    - `@Singular List<Item> items` creates `builder.item(item1).item(item2)` methods
    - `@Singular("missingData") List<Data> missingData` for custom singular names
    - Results in immutable lists automatically - no need for `List.copyOf()`
  - **@Value for immutable data classes**: Creates immutable value objects with all fields final
    - Generates getters, equals/hashCode, toString automatically
    - Combined with @Builder provides both immutability and flexible construction
  - **Prefer immutable data structures**: Both @Singular and manual `List.of()` create immutable lists
- **Follow functional programming principles**:
  - **Return values instead of mutating parameters**: Methods should return new values rather than modifying inputs
    - ❌ Bad: `void processData(Data data, Result result) { result.add(...); }` - mutating parameter
    - ✅ Good: `List<Item> processData(Data data) { return items; }` - returning new value
  - **Prefer pure functions**: Functions should depend only on their inputs and produce consistent outputs
    - No side effects like modifying external state
    - Makes code more testable and predictable
  - **Use immutable data structures**: Prevent accidental mutations and make code thread-safe
  - **Compose small functions**: Build complex behavior by composing simple, focused functions
    - ❌ Bad: One large method doing multiple things with mutations
    - ✅ Good: Several small methods returning values that are combined
- **Law of Demeter (Principle of Least Knowledge)**: An object should only talk to its immediate friends, not strangers
  - Don't chain through objects: `a.getB().getC().doSomething()` - this couples you to the entire chain
  - Instead, push behavior down: give each object a method that encapsulates the knowledge it needs
  - ❌ Bad: `entry.getAccount().getPurpose() == USER_ACCOUNT` - reaching through entry to account's internals
  - ✅ Good: `account.isUserAccount()` + `entry.isUserFundUnit()` - each object answers questions about itself
  - Ask, don't inspect: instead of pulling data out of an object and making decisions, ask the object to make the decision
  - ❌ Bad: `if (transaction.getEntries().stream().filter(e -> e.getAssetType() == FUND_UNIT)...)` - pulling internals out
  - ✅ Good: `transaction.findUserFundUnits()` - let the object that owns the data answer the question
  - This makes code more resilient to change: if internal structure changes, only the owning class needs updating
- **OOP: Push behavior to where the data lives**: Methods should live on the class that owns the data they operate on
  - If you're writing a method that mostly accesses another object's fields, move it to that object
  - Create small, focused query methods on domain objects (e.g., `isUserAccount()`, `isUserFundUnit()`, `findUserFundUnits()`)
  - This naturally leads to better encapsulation and testability
  - ❌ Bad: Utility/service method that inspects an object's internals from the outside
  - ✅ Good: Domain object method that encapsulates its own logic
- **Write code as if using Kotlin (immutable and null-safe)**:
  - **Default to immutability**: Prevent reassignment where it matters
    - Use `final` keyword for **fields** (instance variables) and **public API parameters** (method parameters in public/protected methods)
    - **Do NOT use `final` for local variables** within method scope - it's verbose and provides minimal benefit
    - **Exception**: Use `final` for local variables when it **simplifies code or avoids duplication** (e.g., extracting common values to reuse)
    - ❌ Bad: `private String name;` then reassigning it - mutable field
    - ✅ Good: `private final String name;` - immutable field
    - ❌ Overkill: `final String localVar = "value";` inside a method - unnecessary verbosity
    - ✅ Good: `String localVar = "value";` inside a method - scope already limits visibility
    - ✅ Also good: `final Map<String, Object> baseData = Map.of(...);` when reused to avoid duplication
  - **Null safety**: Prefer Optional or default values, but returning null is acceptable for fail-fast behavior
    - Use `Optional` or default values when the absence of a value is a valid state
    - Return `null` when you want to fail fast with NullPointerException rather than fail silently
    - ❌ Bad: `return "unknown";` when value is missing - fails silently, harder to debug
    - ✅ Good: `return null;` when missing value should cause immediate failure
    - ✅ Also good: `return Optional.empty();` when absence is a valid state to handle
    - ✅ Also good: `return List.of();` for empty collections - never return null for collections
  - **Use @NonNull annotations**: Make nullability explicit in APIs
  - **Validate inputs early**: Check for nulls at method entry, fail fast with clear messages
  - **Prefer value objects**: Use immutable classes/records for data
    - Records are perfect for this: `public record User(String name, int age) {}`
  - **Builder pattern for complex objects**: When many parameters exist, use builders
  - **Prefer immutable data structures**: Use `List.of()`, `Set.of()`, `Map.of()` instead of mutable collections
    - ❌ Bad: `Map<String, Object> data = new HashMap<>(); data.put("key", value);` - mutable, verbose
    - ✅ Good: `Map<String, Object> data = Map.of("key", value);` - immutable, concise
    - Use immutable structures when it simplifies code or avoids duplication
    - Extract common data to avoid repeating Map/List creation
  - **Always prefer Java Streams over for loops**: Use functional stream operations for better readability and composability
    - Streams are more declarative (what you want, not how to get it)
    - Easier to parallelize, compose, and test
    - Avoid mutable state and side effects
    - ❌ Bad: Traditional for loop with mutation
      ```java
      List<Result> results = new ArrayList<>();
      for (Item item : items) {
        if (item.isValid()) {
          results.add(transform(item));
        }
      }
      return results;
      ```
    - ✅ Good: Stream with filter and map
      ```java
      return items.stream()
          .filter(Item::isValid)
          .map(this::transform)
          .toList();
      ```
    - ❌ Bad: For loop over map entries
      ```java
      List<Result> results = new ArrayList<>();
      for (Map.Entry<K, V> entry : map.entrySet()) {
        if (condition(entry)) {
          results.add(process(entry));
        }
      }
      ```
    - ✅ Good: Stream over map entries
      ```java
      return map.entrySet().stream()
          .filter(this::condition)
          .map(this::process)
          .toList();
      ```
    - Use `Objects::nonNull` for filtering nulls instead of lambda: `.filter(Objects::nonNull)`
    - Prefer method references over lambdas when possible for better readability

#### Assertions Best Practices
- **Always use AssertJ** for assertions instead of JUnit assertions for better readability and error messages
- **Never assert on exception messages or log messages** - these are implementation details that can change:
  - ❌ Bad: `assertThat(exception.getMessage()).contains("not match")`
  - ✅ Good: `assertThrows(IllegalStateException.class, () -> service.method())`
  - Exception messages and log outputs are not part of the API contract
- **Write fluent, readable assertions** that clearly express intent:
  - ✅ Good: `assertThat(result).isEqualTo(expected)`
  - ✅ Good: `assertThat(list).containsExactly(item1, item2)`
- **Compare complete objects/collections** instead of individual properties:
  - ❌ Bad: `assertThat(list).hasSize(1)` → Less informative on failure
  - ❌ Bad: `assertThat(list.size()).isEqualTo(1)` → Even worse - cryptic error messages
  - ✅ Good: `assertThat(list).isEqualTo(List.of(expectedItem))` → Shows full comparison
  - ✅ Good: `assertThat(list).singleElement().satisfies(item -> ...)` → Clear intent and good errors
  - Always assert on the full collection/object to get meaningful failure messages
- **DTOs should have meaningful toString() implementations** to support readable assertion failures
- **Use AssertJ's descriptive methods**:
  - `containsExactly()` for ordered collections
  - `containsExactlyInAnyOrder()` for unordered collections
  - `isNotEmpty()`, `isEmpty()` for null/empty checks
  - `contains()`, `doesNotContain()` for partial matches

#### Mocking Strategy
- **Minimal mocking principle**: Use mocking as little as possible, but as much as necessary
- **Only mock injected dependencies**: Typically only mock class dependencies that are injected via constructor or field injection
- **Prefer real instances**: Always prefer using real instances of data objects over mocks
- **Never mock or spy on data classes**: Don't use Mockito spies or mocks for POJOs, DTOs, or entity classes
- **Avoid ArgumentCaptor**: ArgumentCaptor tightly couples tests to implementation details. Prefer asserting on return values (pure functions), and when side effects are unavoidable, use direct `verify` with the expected object instead of capturing and inspecting:
  - ✅ Best: `assertThat(service.process(input)).isEqualTo(expectedResult)` — test pure input/output, no mocks needed
  - ✅ Acceptable: `verify(publisher).publishEvent(new MyEvent(2, amount))` — when side effects are unavoidable
  - ❌ Bad: `var captor = ArgumentCaptor.forClass(MyEvent.class); verify(publisher).publishEvent(captor.capture()); assertThat(captor.getValue().count()).isEqualTo(2);`

#### Test Data
- **Use Test Fixtures**: Create and use TestFixture classes for generating test data
  - Example: `LedgerAccountFixture` for creating `LedgerAccount` instances with preset data
  - Fixtures should provide flexible methods with sensible defaults
  - Consider adding overloaded methods for different scenarios (e.g., with/without dates)
- **Real object construction**: Construct real instances of value objects, DTOs, and domain objects in tests

#### Example Test Structure
```java
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.example.domain.Status.SUCCESS;
import static com.example.fixtures.AccountFixture.anAccount;
import static java.math.BigDecimal.TEN;

@ExtendWith(MockitoExtension.class)
class ServiceTest {

  @Mock
  private DependencyService dependencyService; // Only mock injected services

  @InjectMocks
  private ServiceUnderTest service;

  @Test
  void should_doSomething_whenCondition() {
    // Given - use real instances and fixtures
    var inputData = new DataObject("test", 123);
    var account = anAccount().withBalance(TEN);
    var expectedResult = new Result("expected", SUCCESS, List.of(item1, item2));

    when(dependencyService.fetchData()).thenReturn(account);

    // When
    var result = service.processData(inputData);

    // Then - use AssertJ for readable, fluent assertions
    assertThat(result).isEqualTo(expectedResult);

    // For exceptions - only assert the type, not the message
    assertThrows(IllegalArgumentException.class, () -> service.validate(null));
  }
}
```

### Development Setup Requirements
- Spring profile must be set to `dev` for local development (includes DB migration)
- Use `dev,mock` profiles together to mock external services during development
- PostgreSQL database running locally or via Docker
- Java 25 with preview features enabled
- File encoding must be UTF-8

## Controller Architecture

### Keep Controllers Lean
Controllers should be thin routing layers with minimal logic. They should only:
- Parse and validate request data (using built-in Spring annotations)
- Delegate to service layer for business logic
- Handle HTTP concerns (status codes, headers)
- Return responses

**Never put business logic in controllers** - extract it into dedicated service or helper classes.

### Extract Complex Logic into Separate Classes

When controllers need complex operations (signature verification, encryption, data transformation), extract them into dedicated `@Component` classes:

- **Signature/Authentication**: Create `*Verifier` or `*Authenticator` classes
  - Example: `MandrillSignatureVerifier`, `JwtTokenVerifier`
- **Data Transformation**: Create `*Mapper` or `*Converter` classes
  - Example: `PaymentResponseMapper`, `XmlToJsonConverter`
- **Validation**: Create `*Validator` classes
  - Example: `BankStatementValidator`, `PersonalCodeValidator`

These classes should be:
- Annotated with `@Component` for Spring injection
- Focused on a single responsibility
- Easily testable in isolation
- Reusable across multiple controllers if needed
