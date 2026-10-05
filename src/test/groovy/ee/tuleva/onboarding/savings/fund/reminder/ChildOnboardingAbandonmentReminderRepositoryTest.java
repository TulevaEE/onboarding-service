package ee.tuleva.onboarding.savings.fund.reminder;

import static ee.tuleva.onboarding.notification.email.EmailStatus.SENT;
import static ee.tuleva.onboarding.notification.email.EmailType.SAVINGS_FUND_FIRST_PAYMENT_REMINDER_CHILD;
import static ee.tuleva.onboarding.notification.email.EmailType.SAVINGS_FUND_ONBOARDING_ABANDONMENT_CHILD;
import static ee.tuleva.onboarding.party.ParentChildLinkStatus.ACTIVE;
import static ee.tuleva.onboarding.party.ParentChildLinkStatus.PENDING_KYC;
import static ee.tuleva.onboarding.party.PartyId.Type.PERSON;
import static ee.tuleva.onboarding.party.RepresentationType.GUARDIAN;
import static ee.tuleva.onboarding.party.RepresentationType.LEGAL_REPRESENTATIVE;
import static ee.tuleva.onboarding.savings.SavingsFundOnboardingStatus.COMPLETED;
import static ee.tuleva.onboarding.savings.SavingsFundOnboardingStatus.PENDING;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.util.Locale.ENGLISH;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.auth.principal.PersonImpl;
import ee.tuleva.onboarding.kyc.survey.KycSurvey;
import ee.tuleva.onboarding.kyc.survey.KycSurveyResponse;
import ee.tuleva.onboarding.notification.email.Email;
import ee.tuleva.onboarding.notification.email.EmailType;
import ee.tuleva.onboarding.party.ParentChildLink;
import ee.tuleva.onboarding.party.ParentChildLinkRepository;
import ee.tuleva.onboarding.savings.SavingsFundOnboardingStatus;
import ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingRepository;
import ee.tuleva.onboarding.time.ClockConfig;
import ee.tuleva.onboarding.time.ClockHolder;
import ee.tuleva.onboarding.user.User;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
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
  ChildOnboardingAbandonmentReminderRepository.class,
  SavingsFundOnboardingRepository.class,
  ClockConfig.class
})
class ChildOnboardingAbandonmentReminderRepositoryTest {

  private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");
  private static final Instant STARTED_FROM = NOW.minus(30, DAYS);
  private static final Instant STARTED_UNTIL = NOW.minus(3, DAYS);
  private static final LocalDate LATEST_SNAPSHOT = LocalDate.of(2026, 9, 30);

  private static final Locale ESTONIAN = Locale.of("et");

  private static final String PARENT = "37508295796";
  private static final String OTHER_PARENT = "39001109103";
  private static final String CHILD = "66003229972";
  private static final String SECOND_CHILD = "66112229833";

  @Autowired ChildOnboardingAbandonmentReminderRepository repository;
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
  void remindsTheParentWhoStartedAChildAccountInTheWindowAndLeftItUnfinished() {
    var parent = user(PARENT);
    childAccountStarted(CHILD, NOW.minus(10, DAYS));
    childOf(PARENT, CHILD);

    var reminders = repository.fetch(STARTED_FROM, STARTED_UNTIL);

    assertThat(reminders)
        .containsExactly(
            new ChildOnboardingAbandonmentReminder(
                parent.getId(),
                new PersonImpl(PARENT, "Parent " + PARENT, "Example"),
                PARENT + "@example.com",
                ESTONIAN));
  }

  @Test
  void leavesOutChildAccountsStartedTooRecentlyOrTooLongAgo() {
    user(PARENT);
    user(OTHER_PARENT);
    childAccountStarted(CHILD, NOW.minus(1, DAYS));
    childOf(PARENT, CHILD);
    childAccountStarted(SECOND_CHILD, NOW.minus(40, DAYS));
    childOf(OTHER_PARENT, SECOND_CHILD);

    var reminders = repository.fetch(STARTED_FROM, STARTED_UNTIL);

    assertThat(reminders).isEmpty();
  }

  @Test
  void countsTheDaysFromWhenTheParentStartedNotFromWhenTheRowLastChanged() {
    user(PARENT);
    childAccountStarted(CHILD, NOW.minus(40, DAYS));
    childAccountTouched(CHILD, NOW.minus(10, DAYS));
    childOf(PARENT, CHILD);

    var reminders = repository.fetch(STARTED_FROM, STARTED_UNTIL);

    assertThat(reminders).isEmpty();
  }

  @Test
  void leavesOutChildAccountsWhoseSurveyWasSubmittedAndAwaitsOurReview() {
    user(PARENT);
    var child = user(CHILD);
    childAccountStarted(CHILD, NOW.minus(10, DAYS));
    childOf(PARENT, CHILD);
    surveySubmittedBy(child);

    var reminders = repository.fetch(STARTED_FROM, STARTED_UNTIL);

    assertThat(reminders).isEmpty();
  }

  @Test
  void leavesOutChildAccountsThatWereOpened() {
    user(PARENT);
    childAccount(CHILD, COMPLETED, NOW.minus(10, DAYS));
    childOf(PARENT, CHILD);

    var reminders = repository.fetch(STARTED_FROM, STARTED_UNTIL);

    assertThat(reminders).isEmpty();
  }

  @Test
  void remindsAParentOnceHoweverManyChildAccountsTheyLeftUnfinished() {
    user(PARENT);
    childAccountStarted(CHILD, NOW.minus(10, DAYS));
    childOf(PARENT, CHILD);
    childAccountStarted(SECOND_CHILD, NOW.minus(5, DAYS));
    childOf(PARENT, SECOND_CHILD);

    var reminders = repository.fetch(STARTED_FROM, STARTED_UNTIL);

    assertThat(reminders)
        .extracting(reminder -> reminder.parent().getPersonalCode())
        .containsExactly(PARENT);
  }

  @Test
  void doesNotRemindAParentAgainAboutAnAttemptTheyWereAlreadyRemindedOf() {
    user(PARENT);
    childAccountStarted(CHILD, NOW.minus(10, DAYS));
    childOf(PARENT, CHILD);
    emailSentTo(PARENT, SAVINGS_FUND_ONBOARDING_ABANDONMENT_CHILD, NOW.minus(5, DAYS));

    var reminders = repository.fetch(STARTED_FROM, STARTED_UNTIL);

    assertThat(reminders).isEmpty();
  }

  @Test
  void remindsAParentAgainAboutAChildAccountStartedAfterTheirLastReminder() {
    user(PARENT);
    emailSentTo(PARENT, SAVINGS_FUND_ONBOARDING_ABANDONMENT_CHILD, NOW.minus(20, DAYS));
    childAccountStarted(CHILD, NOW.minus(10, DAYS));
    childOf(PARENT, CHILD);

    var reminders = repository.fetch(STARTED_FROM, STARTED_UNTIL);

    assertThat(reminders)
        .extracting(reminder -> reminder.parent().getPersonalCode())
        .containsExactly(PARENT);
  }

  @Test
  void stillRemindsAParentWhoGotADifferentEmail() {
    user(PARENT);
    childAccountStarted(CHILD, NOW.minus(10, DAYS));
    childOf(PARENT, CHILD);
    emailSentTo(PARENT, SAVINGS_FUND_FIRST_PAYMENT_REMINDER_CHILD, NOW.minus(5, DAYS));

    var reminders = repository.fetch(STARTED_FROM, STARTED_UNTIL);

    assertThat(reminders)
        .extracting(reminder -> reminder.parent().getPersonalCode())
        .containsExactly(PARENT);
  }

  @Test
  void remindsParentsWhoPreferEnglishInEnglish() {
    user(PARENT);
    childAccountStarted(CHILD, NOW.minus(10, DAYS));
    childOf(PARENT, CHILD);
    languagePreference(PARENT, "ENG", LATEST_SNAPSHOT);

    var reminders = repository.fetch(STARTED_FROM, STARTED_UNTIL);

    assertThat(reminders)
        .extracting(ChildOnboardingAbandonmentReminder::locale)
        .containsExactly(ENGLISH);
  }

  @Test
  void remindsParentsWhoPreferEstonianInEstonian() {
    user(PARENT);
    childAccountStarted(CHILD, NOW.minus(10, DAYS));
    childOf(PARENT, CHILD);
    languagePreference(PARENT, "EST", LATEST_SNAPSHOT);

    var reminders = repository.fetch(STARTED_FROM, STARTED_UNTIL);

    assertThat(reminders)
        .extracting(ChildOnboardingAbandonmentReminder::locale)
        .containsExactly(ESTONIAN);
  }

  @Test
  void followsTheLanguagePreferenceInTheLatestRegistrySnapshot() {
    user(PARENT);
    user(OTHER_PARENT);
    childAccountStarted(CHILD, NOW.minus(10, DAYS));
    childOf(PARENT, CHILD);
    childAccountStarted(SECOND_CHILD, NOW.minus(10, DAYS));
    childOf(OTHER_PARENT, SECOND_CHILD);
    languagePreference(PARENT, "ENG", LATEST_SNAPSHOT.minusDays(1));
    languagePreference(PARENT, "EST", LATEST_SNAPSHOT);
    languagePreference(OTHER_PARENT, "ENG", LATEST_SNAPSHOT.minusDays(1));

    var reminders = repository.fetch(STARTED_FROM, STARTED_UNTIL);

    assertThat(reminders)
        .extracting(ChildOnboardingAbandonmentReminder::locale)
        .containsExactly(ESTONIAN, ESTONIAN);
  }

  @Test
  void leavesOutTheCoParentWhoseLinkWasCreatedForThemAndIsNotActive() {
    user(PARENT);
    user(OTHER_PARENT);
    childAccountStarted(CHILD, NOW.minus(10, DAYS));
    childOf(PARENT, CHILD);
    linked(linkOf(OTHER_PARENT, CHILD).status(PENDING_KYC));

    var reminders = repository.fetch(STARTED_FROM, STARTED_UNTIL);

    assertThat(reminders)
        .extracting(reminder -> reminder.parent().getPersonalCode())
        .containsExactly(PARENT);
  }

  @Test
  void leavesOutGuardiansWhoseLinkOperationsCreatedForAnAdultWard() {
    user(PARENT);
    childAccountStarted(OTHER_PARENT, NOW.minus(10, DAYS));
    linked(linkOf(PARENT, OTHER_PARENT).relationshipType(GUARDIAN));

    var reminders = repository.fetch(STARTED_FROM, STARTED_UNTIL);

    assertThat(reminders).isEmpty();
  }

  @Test
  void leavesOutParentsWhoseRepresentationIsSuspended() {
    user(PARENT);
    childAccountStarted(CHILD, NOW.minus(10, DAYS));
    linked(linkOf(PARENT, CHILD).suspendedAt(NOW.minus(1, DAYS)));

    var reminders = repository.fetch(STARTED_FROM, STARTED_UNTIL);

    assertThat(reminders).isEmpty();
  }

  @Test
  void leavesOutParentsWhoseRepresentationEndsTodayAsTheChildComesOfAge() {
    user(PARENT);
    childAccountStarted(CHILD, NOW.minus(10, DAYS));
    linked(linkOf(PARENT, CHILD).validUntil(LocalDate.ofInstant(NOW, ZoneOffset.UTC)));

    var reminders = repository.fetch(STARTED_FROM, STARTED_UNTIL);

    assertThat(reminders).isEmpty();
  }

  @Test
  void leavesOutParentsWithABlankEmailAddress() {
    entityManager.persist(userBuilder(PARENT).email("").build());
    childAccountStarted(CHILD, NOW.minus(10, DAYS));
    childOf(PARENT, CHILD);

    var reminders = repository.fetch(STARTED_FROM, STARTED_UNTIL);

    assertThat(reminders).isEmpty();
  }

  @Test
  void leavesOutParentsWithoutAnEmailAddress() {
    entityManager.persist(userBuilder(PARENT).email(null).build());
    childAccountStarted(CHILD, NOW.minus(10, DAYS));
    childOf(PARENT, CHILD);

    var reminders = repository.fetch(STARTED_FROM, STARTED_UNTIL);

    assertThat(reminders).isEmpty();
  }

  private User user(String personalCode) {
    return entityManager.persist(userBuilder(personalCode).build());
  }

  private User.UserBuilder userBuilder(String personalCode) {
    return User.builder()
        .personalCode(personalCode)
        .firstName("Parent " + personalCode)
        .lastName("Example")
        .email(personalCode + "@example.com")
        .createdDate(NOW)
        .updatedDate(NOW)
        .active(true);
  }

  private void childAccountStarted(String childCode, Instant startedAt) {
    childAccount(childCode, PENDING, startedAt);
  }

  private void childAccount(
      String childCode, SavingsFundOnboardingStatus status, Instant startedAt) {
    ClockHolder.setClock(Clock.fixed(startedAt, ZoneOffset.UTC));
    onboardingRepository.saveOnboardingStatus(childCode, PERSON, status);
    ClockHolder.setClock(Clock.fixed(NOW, ZoneOffset.UTC));
    jdbcClient
        .sql("UPDATE savings_fund_onboarding SET created_at = :startedAt WHERE code = :code")
        .param("startedAt", Timestamp.from(startedAt))
        .param("code", childCode)
        .update();
  }

  private void childAccountTouched(String childCode, Instant touchedAt) {
    jdbcClient
        .sql("UPDATE savings_fund_onboarding SET updated_at = :touchedAt WHERE code = :code")
        .param("touchedAt", Timestamp.from(touchedAt))
        .param("code", childCode)
        .update();
  }

  private void surveySubmittedBy(User child) {
    entityManager.persist(
        KycSurvey.builder().userId(child.getId()).survey(new KycSurveyResponse(List.of())).build());
    entityManager.flush();
  }

  private void emailSentTo(String personalCode, EmailType type, Instant sentAt) {
    ClockHolder.setClock(Clock.fixed(sentAt, ZoneOffset.UTC));
    entityManager.persistAndFlush(
        Email.builder()
            .personalCode(personalCode)
            .mandrillMessageId("message-" + personalCode)
            .type(type)
            .status(SENT)
            .build());
    ClockHolder.setClock(Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private void childOf(String parentCode, String childCode) {
    linked(linkOf(parentCode, childCode));
  }

  private void linked(ParentChildLink.ParentChildLinkBuilder link) {
    parentChildLinkRepository.save(link.build());
    entityManager.flush();
  }

  private ParentChildLink.ParentChildLinkBuilder linkOf(String parentCode, String childCode) {
    return ParentChildLink.builder()
        .parentPersonalCode(parentCode)
        .childPersonalCode(childCode)
        .relationshipType(LEGAL_REPRESENTATIVE)
        .status(ACTIVE)
        .validUntil(LocalDate.of(2030, 1, 1));
  }

  private void languagePreference(
      String personalCode, String languagePreference, LocalDate snapshotDate) {
    jdbcClient
        .sql(
            """
            INSERT INTO unit_owner (personal_id, language_preference, date_created, snapshot_date)
            VALUES (:personalCode, :languagePreference, :dateCreated, :snapshotDate)
            """)
        .param("personalCode", personalCode)
        .param("languagePreference", languagePreference)
        .param("dateCreated", Timestamp.from(NOW))
        .param("snapshotDate", snapshotDate)
        .update();
  }
}
