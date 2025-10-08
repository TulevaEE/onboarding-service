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
- **Run all tests**: `./gradlew test`
- **Run specific test class**: `./gradlew test --tests MyTest`
- **Run with test output**: `./gradlew test --info`
- **Code coverage report**: `./gradlew jacocoTestReport` (report in `build/reports/jacoco/test/html/index.html`)
- **Integration tests**: `./gradlew integrationTest`

### Code Quality
- **Apply formatting**: `./gradlew spotlessApply`
- **Check formatting**: `./gradlew spotlessCheck`
- **Check dependencies**: `./gradlew dependencyCheckAnalyze`

### Database
- **Run migrations**: Automatic with `dev` profile
- **Generate migration**: Create file in `src/main/resources/db/migration/` following naming convention

### Important Notes
- Always set Spring active profile to `dev` for local development
- Use `mock` profile to mock EpisService if you don't want to run epis-service
- The application uses Java 21 with preview features enabled
- Code formatting with Google Java style is enforced via Spotless
- High test coverage requirements are enforced for AML and deadline packages (100% class/method/line coverage)

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

#### Test Framework
- Spock framework (Groovy) for unit and integration tests
- JUnit 5 tests are also acceptable
- H2 in-memory database for test isolation
- Snapshot testing for complex responses
- MockServer for external service mocking
- High coverage requirements for critical domains (AML, deadlines)

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
  - ✅ Good: `assertThat(list).isEqualTo(List.of(expectedItem))` → Shows full comparison
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
