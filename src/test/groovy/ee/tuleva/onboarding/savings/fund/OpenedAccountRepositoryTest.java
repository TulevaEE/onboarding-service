package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.party.ParentChildLinkStatus.ACTIVE;
import static ee.tuleva.onboarding.party.ParentChildLinkStatus.PENDING_KYC;
import static ee.tuleva.onboarding.party.PartyId.Type.PERSON;
import static ee.tuleva.onboarding.party.RepresentationType.LEGAL_REPRESENTATIVE;
import static ee.tuleva.onboarding.savings.SavingsFundOnboardingStatus.COMPLETED;
import static ee.tuleva.onboarding.savings.SavingsFundOnboardingStatus.PENDING;
import static java.time.temporal.ChronoUnit.HOURS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import ee.tuleva.onboarding.company.Company;
import ee.tuleva.onboarding.party.ParentChildLink;
import ee.tuleva.onboarding.party.ParentChildLinkRepository;
import ee.tuleva.onboarding.party.ParentChildLinkStatus;
import ee.tuleva.onboarding.savings.SavingsFundOnboardingStatus;
import ee.tuleva.onboarding.time.ClockConfig;
import ee.tuleva.onboarding.time.ClockHolder;
import ee.tuleva.onboarding.user.User;
import java.sql.Timestamp;
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
@Import({OpenedAccountRepository.class, SavingsFundOnboardingRepository.class, ClockConfig.class})
class OpenedAccountRepositoryTest {

  private static final Instant NOW = Instant.parse("2026-10-08T06:00:00Z");
  private static final Instant OPENED_FROM = Instant.parse("2026-10-04T21:00:00Z");
  private static final Instant OPENED_UNTIL = Instant.parse("2026-10-07T21:00:00Z");
  private static final Instant IN_THE_WINDOW = OPENED_UNTIL.minus(5, HOURS);

  private static final String ADULT = "38812121215";
  private static final String CHILD = "61506150006";
  private static final String OTHER = "60001019906";

  @Autowired OpenedAccountRepository repository;
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
  void findsAccountsThatOpenedInTheWindowWithTheHoldersDetails() {
    holder(ADULT, "Mari", "Tamm", "mari@example.com");
    opened(ADULT, IN_THE_WINDOW);

    assertThat(repository.fetch(OPENED_FROM, OPENED_UNTIL))
        .containsExactly(
            new OpenedAccount(ADULT, "Mari", "Tamm", "mari@example.com", false, false, false));
  }

  @Test
  void leavesOutAccountsThatOpenedTodayOrBeforeTheWindow() {
    holder(ADULT, "Mari", "Tamm", "mari@example.com");
    opened(ADULT, OPENED_UNTIL.plus(1, HOURS));
    holder(OTHER, "Jaan", "Tamm", "jaan@example.com");
    opened(OTHER, OPENED_FROM.minus(1, HOURS));

    assertThat(repository.fetch(OPENED_FROM, OPENED_UNTIL)).isEmpty();
  }

  @Test
  void leavesOutAccountsThatHaveNotOpened() {
    holder(ADULT, "Mari", "Tamm", "mari@example.com");
    status(ADULT, PENDING, IN_THE_WINDOW);

    assertThat(repository.fetch(OPENED_FROM, OPENED_UNTIL)).isEmpty();
  }

  @Test
  void leavesOutAccountsWhoseWelcomeEmailWasAlreadyClaimed() {
    holder(ADULT, "Mari", "Tamm", "mari@example.com");
    opened(ADULT, IN_THE_WINDOW);
    claimed(ADULT);

    assertThat(repository.fetch(OPENED_FROM, OPENED_UNTIL)).isEmpty();
  }

  @Test
  void leavesOutBoardMembersOfACompanyThatHasAlreadyPaid() {
    holder(ADULT, "Mari", "Tamm", "mari@example.com");
    opened(ADULT, IN_THE_WINDOW);
    boardMemberOfPayingCompany(ADULT, "11111111");

    assertThat(repository.fetch(OPENED_FROM, OPENED_UNTIL)).isEmpty();
  }

  @Test
  void knowsWhichAccountsAParentActivelyRepresents() {
    holder(CHILD, "Kati", "Tamm", null);
    opened(CHILD, IN_THE_WINDOW);
    childOf(ADULT, CHILD, ACTIVE);
    holder(OTHER, "Jaan", "Tamm", null);
    opened(OTHER, IN_THE_WINDOW);
    childOf(ADULT, OTHER, PENDING_KYC);

    assertThat(repository.fetch(OPENED_FROM, OPENED_UNTIL))
        .extracting(OpenedAccount::code, OpenedAccount::represented)
        .containsExactlyInAnyOrder(tuple(CHILD, true), tuple(OTHER, false));
  }

  @Test
  void knowsWhetherTheFirstPaymentHasArrived() {
    holder(ADULT, "Mari", "Tamm", "mari@example.com");
    opened(ADULT, IN_THE_WINDOW);
    payment(ADULT, "RECEIVED");
    holder(OTHER, "Jaan", "Tamm", "jaan@example.com");
    opened(OTHER, IN_THE_WINDOW);
    payment(OTHER, "RETURNED");

    assertThat(repository.fetch(OPENED_FROM, OPENED_UNTIL))
        .extracting(OpenedAccount::code, OpenedAccount::paid)
        .containsExactlyInAnyOrder(tuple(ADULT, true), tuple(OTHER, false));
  }

  @Test
  void knowsWhoPrefersEnglishInTheLatestRegistrySnapshot() {
    holder(ADULT, "Mari", "Tamm", "mari@example.com");
    opened(ADULT, IN_THE_WINDOW);
    languagePreference(ADULT, "ENG", LocalDate.of(2026, 10, 6));
    holder(OTHER, "Jaan", "Tamm", "jaan@example.com");
    opened(OTHER, IN_THE_WINDOW);
    languagePreference(OTHER, "ENG", LocalDate.of(2026, 9, 30));
    languagePreference(OTHER, "EST", LocalDate.of(2026, 10, 6));

    assertThat(repository.fetch(OPENED_FROM, OPENED_UNTIL))
        .extracting(OpenedAccount::code, OpenedAccount::prefersEnglish)
        .containsExactlyInAnyOrder(tuple(ADULT, true), tuple(OTHER, false));
  }

  @Test
  void tellsWhetherAnyPersonPrefersEnglishInTheLatestRegistrySnapshot() {
    languagePreference(ADULT, "ENG", LocalDate.of(2026, 10, 6));
    languagePreference(OTHER, "ENG", LocalDate.of(2026, 9, 30));
    languagePreference(OTHER, "EST", LocalDate.of(2026, 10, 6));

    assertThat(repository.prefersEnglish(ADULT)).isTrue();
    assertThat(repository.prefersEnglish(OTHER)).isFalse();
    assertThat(repository.prefersEnglish(CHILD)).isFalse();
  }

  private void holder(String code, String firstName, String lastName, String email) {
    entityManager.persist(
        User.builder()
            .personalCode(code)
            .firstName(firstName)
            .lastName(lastName)
            .email(email)
            .createdDate(NOW)
            .updatedDate(NOW)
            .active(true)
            .build());
  }

  private void opened(String code, Instant openedAt) {
    status(code, COMPLETED, openedAt);
  }

  private void status(String code, SavingsFundOnboardingStatus status, Instant changedAt) {
    ClockHolder.setClock(Clock.fixed(changedAt, ZoneOffset.UTC));
    onboardingRepository.saveOnboardingStatus(code, PERSON, status);
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

  private void boardMemberOfPayingCompany(String code, String registryCode) {
    var company =
        entityManager.persistAndFlush(
            Company.builder().registryCode(registryCode).name("Test OU " + registryCode).build());
    jdbcClient
        .sql(
            """
            INSERT INTO company_party (party_code, party_type, company_id, relationship_type)
            VALUES (:code, 'PERSON', :companyId, 'BOARD_MEMBER')
            """)
        .param("code", code)
        .param("companyId", company.getId())
        .update();
    jdbcClient
        .sql(
            """
            INSERT INTO saving_fund_payment (amount, currency, status, party_type, party_code)
            VALUES (100.00, 'EUR', 'PROCESSED', 'LEGAL_ENTITY', :registryCode)
            """)
        .param("registryCode", registryCode)
        .update();
  }

  private void claimed(String code) {
    jdbcClient
        .sql(
            """
            INSERT INTO savings_fund_account_opened_email_claim (code, created_at)
            VALUES (:code, :createdAt)
            """)
        .param("code", code)
        .param("createdAt", Timestamp.from(NOW))
        .update();
  }

  private void payment(String code, String status) {
    jdbcClient
        .sql(
            """
            INSERT INTO saving_fund_payment (amount, currency, status, party_type, party_code)
            VALUES (50.00, 'EUR', :status, 'PERSON', :code)
            """)
        .param("status", status)
        .param("code", code)
        .update();
  }

  private void languagePreference(String code, String languagePreference, LocalDate snapshot) {
    jdbcClient
        .sql(
            """
            INSERT INTO unit_owner (personal_id, language_preference, date_created, snapshot_date)
            VALUES (:code, :languagePreference, :dateCreated, :snapshotDate)
            """)
        .param("code", code)
        .param("languagePreference", languagePreference)
        .param("dateCreated", Timestamp.from(NOW))
        .param("snapshotDate", snapshot)
        .update();
  }
}
