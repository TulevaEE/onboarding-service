# AGENTS.md

This file provides guidance to AI coding agents when working with code in this repository.

## Commands

### Development
- **Run locally**: `./gradlew bootRun` (runs on port 9000, requires PostgreSQL)
- **Database**: `docker compose up database -d` (starts PostgreSQL locally)
- **Build**: `./gradlew build`
- **Test**: `./gradlew test`
- **Code formatting**: `./gradlew spotlessApply`
- **Check code style**: `./gradlew spotlessCheck`
- **Code coverage**: `./gradlew jacocoTestReport`

### Important Notes
- Always set Spring active profile to `dev` for local development
- Use `mock` profile to mock EpisService if you don't want to run epis-service
- The application uses Java 21 with preview features enabled
- Code formatting with Google Java style is enforced via Spotless
- High test coverage requirements are enforced for AML and deadline packages (100% class/method/line coverage)

## Architecture

This is Tuleva's pension management onboarding service - a Spring Boot application that handles Tuleva Estonian pension fund management.

The name has historical reasons, it does more than onboarding these days.

### Core Domain Structure
The application follows domain-driven design with these main domains:

- **auth**: OAuth2 authentication with Mobile-ID, Smart-ID, and ID-card
- **user**: User management and member registration
- **fund**: Pension fund information and NAV management
- **mandate**: Pension fund switching and mandate management
- **account**: Account statements and transaction history
- **payment**: Payment processing and capital events
- **aml**: Anti-money laundering checks
- **epis**: Integration with Estonian Pension Information System
- **swedbank**: Banking integration for account fetching
- **signature**: Digital signing with Estonian e-ID methods
- **notification**: Email notifications via Mandrill/Mailchimp

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
- Spock framework (Groovy) for unit and integration tests
- JUnit 5 tests are also acceptable
- H2 in-memory database for test isolation
- Snapshot testing for complex responses
- MockServer for external service mocking
- High coverage requirements for critical domains (AML, deadlines)

### Development Setup Requirements
- Spring profile must be set to `dev` for local development (includes DB migration)
- Use `dev,mock` profiles together to mock external services during development
- PostgreSQL database running locally or via Docker
- Java 21 with preview features enabled
- File encoding must be UTF-8
