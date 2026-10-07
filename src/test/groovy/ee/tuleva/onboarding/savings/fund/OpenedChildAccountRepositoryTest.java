package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.notification.email.EmailStatus.SENT;
import static ee.tuleva.onboarding.notification.email.EmailType.SAVINGS_FUND_FIRST_PAYMENT_REMINDER_CHILD;
import static ee.tuleva.onboarding.party.ParentChildLinkStatus.ACTIVE;
import static ee.tuleva.onboarding.party.ParentChildLinkStatus.PENDING_KYC;
import static ee.tuleva.onboarding.party.PartyId.Type.PERSON;
import static ee.tuleva.onboarding.party.RepresentationType.LEGAL_REPRESENTATIVE;
import static ee.tuleva.onboarding.savings.SavingsFundOnboardingStatus.COMPLETED;
import static ee.tuleva.onboarding.savings.SavingsFundOnboardingStatus.PENDING;
import static java.time.temporal.ChronoUnit.HOURS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import ee.tuleva.onboarding.notification.email.Email;
import ee.tuleva.onboarding.notification.email.EmailType;
import ee.tuleva.onboarding.party.ParentChildLink;
import ee.tuleva.onboarding.party.ParentChildLinkRepository;
import ee.tuleva.onboarding.party.ParentChildLinkStatus;
import ee.tuleva.onboarding.savings.SavingsFundOnboardingStatus;
import ee.tuleva.onboarding.time.ClockConfig;
import ee.tuleva.onboarding.time.ClockHolder;
import ee.tuleva.onboarding.user.User;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@DataJpaTest
@Import({
  OpenedChildAccountRepository.class,
  SavingsFundOnboardingRepository.class,
  ClockConfig.class
})
class OpenedChildAccountRepositoryTest {

  private static final Instant NOW = Instant.parse("2026-10-08T06:00:00Z");
  private static final Instant OPENED_FROM = Instant.parse("2026-10-04T21:00:00Z");
  private static final Instant OPENED_UNTIL = Instant.parse("2026-10-07T21:00:00Z");

  private static final String PARENT = "38812121215";
  private static final String CHILD = "61506150006";
  private static final String OTHER_CHILD = "60001019906";

  @Autowired OpenedChildAccountRepository repository;
  @Autowired SavingsFundOnboardingRepository onboardingRepository;
  @Autowired ParentChildLinkRepository parentChildLinkRepository;
  @Autowired TestEntityManager entityManager;
  @Autowired JdbcClient jdbcClient;

  @BeforeEach
  void freezeClock() {
    ClockHolder.setClock(Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @AfterEach
  void resetClock() {
    ClockHolder.setDefaultClock();
  }

  @Test
  void findsChildAccountsThatOpenedInTheWindowWithTheChildsName() {
    child(CHILD, "Kati", "Tamm");
    opened(CHILD, OPENED_UNTIL.minus(5, HOURS));
    childOf(PARENT, CHILD, ACTIVE);

    assertThat(repository.fetch(OPENED_FROM, OPENED_UNTIL))
        .containsExactly(new OpenedChildAccount(CHILD, "Kati", "Tamm", false));
  }

  @Test
  void leavesOutAccountsThatOpenedTodayOrBeforeTheWindow() {
    child(CHILD, "Kati", "Tamm");
    opened(CHILD, OPENED_UNTIL.plus(1, HOURS));
    childOf(PARENT, CHILD, ACTIVE);
    child(OTHER_CHILD, "Jaan", "Tamm");
    opened(OTHER_CHILD, OPENED_FROM.minus(1, HOURS));
    childOf(PARENT, OTHER_CHILD, ACTIVE);

    assertThat(repository.fetch(OPENED_FROM, OPENED_UNTIL)).isEmpty();
  }

  @Test
  void leavesOutAccountsThatHaveNotOpened() {
    child(CHILD, "Kati", "Tamm");
    status(CHILD, PENDING, OPENED_UNTIL.minus(5, HOURS));
    childOf(PARENT, CHILD, ACTIVE);

    assertThat(repository.fetch(OPENED_FROM, OPENED_UNTIL)).isEmpty();
  }

  @Test
  void leavesOutAccountsWithNobodyActivelyRepresentingThem() {
    child(CHILD, "Kati", "Tamm");
    opened(CHILD, OPENED_UNTIL.minus(5, HOURS));
    childOf(PARENT, CHILD, PENDING_KYC);

    assertThat(repository.fetch(OPENED_FROM, OPENED_UNTIL)).isEmpty();
  }

  @Test
  void leavesOutAccountsWhoseWelcomeEmailWasAlreadyClaimed() {
    child(CHILD, "Kati", "Tamm");
    opened(CHILD, OPENED_UNTIL.minus(5, HOURS));
    childOf(PARENT, CHILD, ACTIVE);
    claimed(CHILD);

    assertThat(repository.fetch(OPENED_FROM, OPENED_UNTIL)).isEmpty();
  }

  @Test
  void stillWelcomesAnAccountThatGotADifferentEmail() {
    child(CHILD, "Kati", "Tamm");
    opened(CHILD, OPENED_UNTIL.minus(5, HOURS));
    childOf(PARENT, CHILD, ACTIVE);
    emailFor(CHILD, SAVINGS_FUND_FIRST_PAYMENT_REMINDER_CHILD);

    assertThat(repository.fetch(OPENED_FROM, OPENED_UNTIL)).hasSize(1);
  }

  @Test
  void knowsWhetherTheFirstPaymentHasArrived() {
    child(CHILD, "Kati", "Tamm");
    opened(CHILD, OPENED_UNTIL.minus(5, HOURS));
    childOf(PARENT, CHILD, ACTIVE);
    payment(CHILD, "RECEIVED");
    child(OTHER_CHILD, "Jaan", "Tamm");
    opened(OTHER_CHILD, OPENED_UNTIL.minus(5, HOURS));
    childOf(PARENT, OTHER_CHILD, ACTIVE);
    payment(OTHER_CHILD, "RETURNED");

    assertThat(repository.fetch(OPENED_FROM, OPENED_UNTIL))
        .extracting(OpenedChildAccount::childCode, OpenedChildAccount::paid)
        .containsExactlyInAnyOrder(tuple(CHILD, true), tuple(OTHER_CHILD, false));
  }

  private void child(String personalCode, String firstName, String lastName) {
    entityManager.persist(
        User.builder()
            .personalCode(personalCode)
            .firstName(firstName)
            .lastName(lastName)
            .createdDate(NOW)
            .updatedDate(NOW)
            .active(true)
            .build());
  }

  private void opened(String childCode, Instant openedAt) {
    status(childCode, COMPLETED, openedAt);
  }

  private void status(String childCode, SavingsFundOnboardingStatus status, Instant changedAt) {
    ClockHolder.setClock(Clock.fixed(changedAt, ZoneOffset.UTC));
    onboardingRepository.saveOnboardingStatus(childCode, PERSON, status);
    ClockHolder.setClock(Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private void childOf(String parentCode, String childCode, ParentChildLinkStatus status) {
    parentChildLinkRepository.save(
        ParentChildLink.builder()
            .parentPersonalCode(parentCode)
            .childPersonalCode(childCode)
            .relationshipType(LEGAL_REPRESENTATIVE)
            .status(status)
            .validUntil(LocalDate.of(2030, 1, 1))
            .build());
    entityManager.flush();
  }

  private void claimed(String childCode) {
    jdbcClient
        .sql(
            """
            INSERT INTO child_account_opened_email_claim (child_code, created_at)
            VALUES (:childCode, :createdAt)
            """)
        .param("childCode", childCode)
        .param("createdAt", java.sql.Timestamp.from(NOW))
        .update();
  }

  private void emailFor(String personalCode, EmailType type) {
    entityManager.persistAndFlush(
        Email.builder()
            .personalCode(personalCode)
            .mandrillMessageId("message-" + personalCode)
            .type(type)
            .status(SENT)
            .build());
  }

  private void payment(String childCode, String status) {
    jdbcClient
        .sql(
            """
            INSERT INTO saving_fund_payment (amount, currency, status, party_type, party_code)
            VALUES (50.00, 'EUR', :status, 'PERSON', :childCode)
            """)
        .param("status", status)
        .param("childCode", childCode)
        .update();
  }
}
