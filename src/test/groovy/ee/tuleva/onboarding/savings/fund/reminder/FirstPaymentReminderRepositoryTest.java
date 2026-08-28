package ee.tuleva.onboarding.savings.fund.reminder;

import static ee.tuleva.onboarding.mandate.email.persistence.EmailStatus.SENT;
import static ee.tuleva.onboarding.mandate.email.persistence.EmailType.SAVINGS_FUND_FIRST_PAYMENT_REMINDER_PERSON;
import static ee.tuleva.onboarding.party.PartyId.Type.PERSON;
import static ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingStatus.COMPLETED;
import static java.time.temporal.ChronoUnit.DAYS;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.company.Company;
import ee.tuleva.onboarding.mandate.email.persistence.Email;
import ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingRepository;
import ee.tuleva.onboarding.time.ClockConfig;
import ee.tuleva.onboarding.time.ClockHolder;
import ee.tuleva.onboarding.user.User;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;
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
  FirstPaymentReminderRepository.class,
  SavingsFundOnboardingRepository.class,
  ClockConfig.class
})
class FirstPaymentReminderRepositoryTest {

  private static final Instant NOW = Instant.parse("2026-09-01T12:00:00Z");
  private static final Instant OPENED_FROM = NOW.minus(30, DAYS);
  private static final Instant OPENED_UNTIL = NOW.minus(7, DAYS);

  private static final String SAVER = "38812121215";
  private static final String ENGLISH_SPEAKING_SAVER = "38001085718";
  private static final String JUST_OPENED = "39901019992";
  private static final String OPENED_LONG_AGO = "40404049996";
  private static final String ALREADY_PAID = "60001019906";
  private static final String ALREADY_REMINDED = "30303039816";
  private static final String CHILD = "61506150006";

  @Autowired FirstPaymentReminderRepository repository;
  @Autowired SavingsFundOnboardingRepository onboardingRepository;
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
  void fetchesSaversWhoOpenedAnAccountInTheWindowAndNeverPaid() {
    accountOpened(SAVER, NOW.minus(10, DAYS));
    accountOpened(JUST_OPENED, NOW.minus(2, DAYS));
    accountOpened(OPENED_LONG_AGO, NOW.minus(40, DAYS));

    accountOpened(ALREADY_PAID, NOW.minus(10, DAYS));
    payment(ALREADY_PAID, "RETURNED");

    accountOpened(ALREADY_REMINDED, NOW.minus(10, DAYS));
    reminderSentTo(ALREADY_REMINDED);

    accountOpened(CHILD, NOW.minus(10, DAYS));
    childOf(SAVER, CHILD);

    var reminders = repository.fetchForAdults(OPENED_FROM, OPENED_UNTIL);

    assertThat(reminders)
        .containsExactly(
            new FirstPaymentReminder(
                SAVER,
                "Saver " + SAVER,
                "Example",
                SAVER + "@example.com",
                Locale.of("et"),
                SAVINGS_FUND_FIRST_PAYMENT_REMINDER_PERSON,
                null));
  }

  @Test
  void sendsInTheLanguageTheSaverPrefers() {
    accountOpened(SAVER, NOW.minus(10, DAYS));
    languagePreference(SAVER, "EST");

    accountOpened(ENGLISH_SPEAKING_SAVER, NOW.minus(10, DAYS));
    languagePreference(ENGLISH_SPEAKING_SAVER, "ENG");

    var reminders = repository.fetchForAdults(OPENED_FROM, OPENED_UNTIL);

    assertThat(reminders)
        .extracting(FirstPaymentReminder::accountCode, FirstPaymentReminder::locale)
        .containsExactlyInAnyOrder(
            org.assertj.core.groups.Tuple.tuple(SAVER, Locale.of("et")),
            org.assertj.core.groups.Tuple.tuple(ENGLISH_SPEAKING_SAVER, Locale.ENGLISH));
  }

  @Test
  void leavesOutMinorsEvenWhenNobodyIsLinkedAsTheirParent() {
    accountOpened(CHILD, NOW.minus(10, DAYS));

    var reminders = repository.fetchForAdults(OPENED_FROM, OPENED_UNTIL);

    assertThat(reminders).isEmpty();
  }

  @Test
  void leavesOutBoardMembersOfACompanyThatHasAlreadyPaid() {
    accountOpened(SAVER, NOW.minus(10, DAYS));
    var payingCompany = company("11111111");
    boardMember(payingCompany, SAVER);
    companyPayment("11111111");

    var reminders = repository.fetchForAdults(OPENED_FROM, OPENED_UNTIL);

    assertThat(reminders).isEmpty();
  }

  @Test
  void remindsBoardMembersWhoseCompanyHasNotPaidEither() {
    accountOpened(SAVER, NOW.minus(10, DAYS));
    var company = company("11111111");
    boardMember(company, SAVER);

    var reminders = repository.fetchForAdults(OPENED_FROM, OPENED_UNTIL);

    assertThat(reminders).extracting(FirstPaymentReminder::accountCode).containsExactly(SAVER);
  }

  @Test
  void remindsEveryGuardianAboutAChildAccountNobodyHasPaidInto() {
    accountOpened(CHILD, NOW.minus(10, DAYS));
    childOf(SAVER, CHILD);
    childOf(ENGLISH_SPEAKING_SAVER, CHILD);
    accountOpened(SAVER, NOW.minus(10, DAYS));
    accountOpened(ENGLISH_SPEAKING_SAVER, NOW.minus(10, DAYS));

    accountOpened(ALREADY_PAID, NOW.minus(10, DAYS));
    childOf(SAVER, ALREADY_PAID);
    payment(ALREADY_PAID, "PROCESSED");

    var reminders = repository.fetchForChildren(OPENED_FROM, OPENED_UNTIL);

    assertThat(reminders)
        .extracting(FirstPaymentReminder::accountCode, FirstPaymentReminder::recipientEmail)
        .containsExactlyInAnyOrder(
            org.assertj.core.groups.Tuple.tuple(CHILD, SAVER + "@example.com"),
            org.assertj.core.groups.Tuple.tuple(CHILD, ENGLISH_SPEAKING_SAVER + "@example.com"));
  }

  @Test
  void namesTheChildTheAccountBelongsTo() {
    accountOpened(CHILD, NOW.minus(10, DAYS));
    childOf(SAVER, CHILD);
    accountOpened(SAVER, NOW.minus(10, DAYS));

    var reminders = repository.fetchForChildren(OPENED_FROM, OPENED_UNTIL);

    assertThat(reminders)
        .extracting(FirstPaymentReminder::accountHolderName)
        .containsExactly("Saver " + CHILD + " Example");
  }

  private Company company(String registryCode) {
    return entityManager.persistAndFlush(
        Company.builder().registryCode(registryCode).name("Test OU " + registryCode).build());
  }

  private void boardMember(Company company, String personalCode) {
    jdbcClient
        .sql(
            """
            INSERT INTO company_party (party_code, party_type, company_id, relationship_type)
            VALUES (:personalCode, 'PERSON', :companyId, 'BOARD_MEMBER')
            """)
        .param("personalCode", personalCode)
        .param("companyId", company.getId())
        .update();
  }

  private void companyPayment(String registryCode) {
    jdbcClient
        .sql(
            """
            INSERT INTO saving_fund_payment (amount, currency, status, party_type, party_code)
            VALUES (100.00, 'EUR', 'PROCESSED', 'LEGAL_ENTITY', :registryCode)
            """)
        .param("registryCode", registryCode)
        .update();
  }

  private void accountOpened(String personalCode, Instant openedAt) {
    entityManager.persist(
        User.builder()
            .personalCode(personalCode)
            .firstName("Saver " + personalCode)
            .lastName("Example")
            .email(personalCode + "@example.com")
            .createdDate(openedAt)
            .updatedDate(openedAt)
            .active(true)
            .build());
    ClockHolder.setClock(Clock.fixed(openedAt, ZoneOffset.UTC));
    onboardingRepository.saveOnboardingStatus(personalCode, PERSON, COMPLETED);
    ClockHolder.setClock(Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private void payment(String personalCode, String status) {
    jdbcClient
        .sql(
            """
            INSERT INTO saving_fund_payment (amount, currency, status, party_type, party_code)
            VALUES (100.00, 'EUR', :status, 'PERSON', :personalCode)
            """)
        .param("status", status)
        .param("personalCode", personalCode)
        .update();
  }

  private void reminderSentTo(String personalCode) {
    entityManager.persist(
        Email.builder()
            .personalCode(personalCode)
            .mandrillMessageId("message-" + personalCode)
            .type(SAVINGS_FUND_FIRST_PAYMENT_REMINDER_PERSON)
            .status(SENT)
            .build());
  }

  private void childOf(String parentPersonalCode, String childPersonalCode) {
    jdbcClient
        .sql(
            """
            INSERT INTO parent_child_link
              (parent_personal_code, child_personal_code, relationship_type, valid_until)
            VALUES (:parent, :child, 'PARENT', :validUntil)
            """)
        .param("parent", parentPersonalCode)
        .param("child", childPersonalCode)
        .param("validUntil", LocalDate.of(2030, 1, 1))
        .update();
  }

  private void languagePreference(String personalCode, String languagePreference) {
    jdbcClient
        .sql(
            """
            INSERT INTO unit_owner (personal_id, language_preference, date_created, snapshot_date)
            VALUES (:personalCode, :languagePreference, :dateCreated, :snapshotDate)
            """)
        .param("personalCode", personalCode)
        .param("languagePreference", languagePreference)
        .param("dateCreated", NOW)
        .param("snapshotDate", LocalDate.of(2026, 8, 31))
        .update();
  }
}
