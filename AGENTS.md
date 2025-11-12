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

### Database
- **Run migrations**: Automatic with `dev` profile
- **Generate migration**: Create file in `src/main/resources/db/migration/` following naming convention

### Important Notes
- Always set Spring active profile to `dev` for local development
- Use `mock` profile to mock EpisService if you don't want to run epis-service
- The application uses Java 21 with preview features enabled
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
- **Backend**: Java 21, Spring Boot 3.5.x, Spring Security, JPA/Hibernate
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

### Testing Strategy

#### CRITICAL: Always Write and Run Tests
- **Write tests for every change**: Every code modification must have corresponding unit or integration tests
- **Run tests before committing**: Always execute tests to ensure they pass before finalizing changes
- **Fix broken tests immediately**: Never leave failing tests in the codebase

#### Test Framework
- Spock framework (Groovy) for unit and integration tests
- JUnit 5 tests are also acceptable
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
- **Self-documenting code over comments**: Follow clean code principles - code should be self-explanatory
  - Use meaningful variable, method, and class names that clearly express intent
  - Extract complex logic into well-named methods
  - **Avoid comments entirely** - they rot and become outdated; code is the source of truth
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
- **Avoid passing null as method parameters**:
  - Use method overloading instead of passing null parameters
  - ❌ Bad: `createTransaction(date, null, metadata, entries)`
  - ✅ Good: Create overloaded method `createTransaction(date, metadata, entries)` that delegates to the full version
  - This makes the API cleaner and prevents NullPointerExceptions
- **Use Lombok @Builder to avoid null constructor arguments**:
  - For classes/records with many optional parameters, use Lombok's @Builder annotation
  - This eliminates the need to pass `null` for unused optional parameters in constructors
  - ❌ Bad: `new MyEvent("type", "id", 123L, null, null, null)` - unclear which parameters are null
  - ✅ Good: `MyEvent.builder().type("type").id("id").ts(123L).build()` - only set what you need
  - Builder pattern makes code more readable and maintainable, especially in tests
  - Works with both classes and records (since Lombok 1.18.20+)

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
- Java 21 with preview features enabled
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
