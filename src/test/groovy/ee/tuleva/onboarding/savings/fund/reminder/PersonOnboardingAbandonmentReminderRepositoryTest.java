package ee.tuleva.onboarding.savings.fund.reminder;

import static ee.tuleva.onboarding.kyc.KycSurveyPurpose.IDENTITY_ONLY;
import static ee.tuleva.onboarding.notification.email.EmailStatus.SENT;
import static ee.tuleva.onboarding.notification.email.EmailType.SAVINGS_FUND_ONBOARDING_ABANDONMENT_CHILD;
import static ee.tuleva.onboarding.notification.email.EmailType.SAVINGS_FUND_ONBOARDING_ABANDONMENT_PERSON;
import static ee.tuleva.onboarding.party.ParentChildLinkStatus.ACTIVE;
import static ee.tuleva.onboarding.party.PartyId.Type.PERSON;
import static ee.tuleva.onboarding.party.RepresentationType.GUARDIAN;
import static ee.tuleva.onboarding.party.RepresentationType.LEGAL_REPRESENTATIVE;
import static ee.tuleva.onboarding.savings.SavingsFundOnboardingStatus.COMPLETED;
import static ee.tuleva.onboarding.savings.SavingsFundOnboardingStatus.PENDING;
import static ee.tuleva.onboarding.savings.SavingsFundOnboardingStatus.REJECTED;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.util.Locale.ENGLISH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import ee.tuleva.onboarding.auth.principal.PersonImpl;
import ee.tuleva.onboarding.kyc.survey.KycSurvey;
import ee.tuleva.onboarding.kyc.survey.KycSurveyResponse;
import ee.tuleva.onboarding.notification.email.Email;
import ee.tuleva.onboarding.notification.email.EmailType;
import ee.tuleva.onboarding.party.ParentChildLink;
import ee.tuleva.onboarding.party.ParentChildLinkRepository;
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
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@EnabledIfEnvironmentVariable(named = "SPRING_PROFILES_ACTIVE", matches = ".*(pg|ci).*")
@DataJpaTest
@Import({
  PersonOnboardingAbandonmentReminderRepository.class,
  SavingsFundOnboardingRepository.class,
  ClockConfig.class
})
class PersonOnboardingAbandonmentReminderRepositoryTest {

  private static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");
  private static final Instant VIEWED_FROM = NOW.minus(30, DAYS);
  private static final Instant VIEWED_UNTIL = NOW.minus(3, DAYS);
  private static final LocalDate LATEST_SNAPSHOT = LocalDate.of(2026, 10, 19);

  private static final Locale ESTONIAN = Locale.of("et");

  private static final String PERSON_SURVEY = "/savings-fund/onboarding/person";

  private static final String SAVER = "37508295796";
  private static final String OTHER_SAVER = "39001109103";
  private static final String THIRD_SAVER = "38812121215";
  private static final String MINOR = "61506150006";
  private static final String CHILD = "66003229972";

  @Autowired PersonOnboardingAbandonmentReminderRepository repository;
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
  void remindsAnAdultWhoOpenedTheSurveyInTheWindowAndNeverSubmittedIt() {
    var saver = user(SAVER);
    viewed(SAVER, PERSON_SURVEY, NOW.minus(10, DAYS));

    var reminders = repository.fetch(VIEWED_FROM, VIEWED_UNTIL);

    assertThat(reminders)
        .containsExactly(
            new PersonOnboardingAbandonmentReminder(
                saver.getId(),
                new PersonImpl(SAVER, "Saver " + SAVER, "Example"),
                SAVER + "@example.com",
                ESTONIAN));
  }

  @Test
  void remindsOnceHoweverManyTimesThePersonOpenedTheSurvey() {
    user(SAVER);
    viewed(SAVER, PERSON_SURVEY, NOW.minus(20, DAYS));
    viewed(SAVER, PERSON_SURVEY, NOW.minus(10, DAYS));
    viewed(SAVER, PERSON_SURVEY, NOW.minus(9, DAYS));

    var reminders = repository.fetch(VIEWED_FROM, VIEWED_UNTIL);

    assertThat(reminders)
        .extracting(reminder -> reminder.person().getPersonalCode())
        .containsExactly(SAVER);
  }

  @Test
  void leavesOutPeopleWhoLastOpenedTheSurveyTooRecentlyOrTooLongAgo() {
    user(SAVER);
    user(OTHER_SAVER);
    viewed(SAVER, PERSON_SURVEY, NOW.minus(1, DAYS));
    viewed(OTHER_SAVER, PERSON_SURVEY, NOW.minus(40, DAYS));

    var reminders = repository.fetch(VIEWED_FROM, VIEWED_UNTIL);

    assertThat(reminders).isEmpty();
  }

  @Test
  void waitsThreeDaysFromTheLastVisitNotTheFirst() {
    user(SAVER);
    viewed(SAVER, PERSON_SURVEY, NOW.minus(10, DAYS));
    viewed(SAVER, PERSON_SURVEY, NOW.minus(1, DAYS));

    var reminders = repository.fetch(VIEWED_FROM, VIEWED_UNTIL);

    assertThat(reminders).isEmpty();
  }

  @Test
  void ignoresVisitsToTheChildAndCompanySurveysAndTheChooser() {
    user(SAVER);
    viewed(SAVER, "/savings-fund/onboarding/child", NOW.minus(10, DAYS));
    viewed(SAVER, "/savings-fund/onboarding/company", NOW.minus(10, DAYS));
    viewed(SAVER, "/savings-fund/onboarding", NOW.minus(10, DAYS));

    var reminders = repository.fetch(VIEWED_FROM, VIEWED_UNTIL);

    assertThat(reminders).isEmpty();
  }

  @Test
  void ignoresPagesThatOnlyStartWithTheSurveyPath() {
    user(SAVER);
    viewed(SAVER, PERSON_SURVEY + "/verify", NOW.minus(10, DAYS));

    var reminders = repository.fetch(VIEWED_FROM, VIEWED_UNTIL);

    assertThat(reminders).isEmpty();
  }

  @Test
  void ignoresOtherEventsOnTheSurveyPage() {
    user(SAVER);
    event(SAVER, "CLICK", PERSON_SURVEY, NOW.minus(10, DAYS));

    var reminders = repository.fetch(VIEWED_FROM, VIEWED_UNTIL);

    assertThat(reminders).isEmpty();
  }

  @Test
  void leavesOutPeopleWithASavingsFundAccountWhetherOpenAwaitingReviewOrRejected() {
    user(SAVER);
    user(OTHER_SAVER);
    user(THIRD_SAVER);
    viewed(SAVER, PERSON_SURVEY, NOW.minus(10, DAYS));
    viewed(OTHER_SAVER, PERSON_SURVEY, NOW.minus(10, DAYS));
    viewed(THIRD_SAVER, PERSON_SURVEY, NOW.minus(10, DAYS));
    onboardingRepository.saveOnboardingStatus(SAVER, PERSON, COMPLETED);
    onboardingRepository.saveOnboardingStatus(OTHER_SAVER, PERSON, PENDING);
    onboardingRepository.saveOnboardingStatus(THIRD_SAVER, PERSON, REJECTED);

    var reminders = repository.fetch(VIEWED_FROM, VIEWED_UNTIL);

    assertThat(reminders).isEmpty();
  }

  @Test
  void leavesOutPeopleWhoSubmittedTheSurvey() {
    var saver = user(SAVER);
    viewed(SAVER, PERSON_SURVEY, NOW.minus(10, DAYS));
    surveySubmittedBy(saver);

    var reminders = repository.fetch(VIEWED_FROM, VIEWED_UNTIL);

    assertThat(reminders).isEmpty();
  }

  @Test
  void leavesOutPeopleWhoseSurveyPredatesThePurposeField() {
    var saver = user(SAVER);
    viewed(SAVER, PERSON_SURVEY, NOW.minus(10, DAYS));
    surveyWithoutPurposeSubmittedBy(saver);

    var reminders = repository.fetch(VIEWED_FROM, VIEWED_UNTIL);

    assertThat(reminders).isEmpty();
  }

  @Test
  void leavesOutAParentWhoseChildAccountMovedAfterTheyOpenedTheSurvey() {
    user(SAVER);
    viewed(SAVER, PERSON_SURVEY, NOW.minus(10, DAYS));
    representedChild(SAVER, CHILD);
    childAccountTouched(CHILD, NOW.minus(9, DAYS));

    var reminders = repository.fetch(VIEWED_FROM, VIEWED_UNTIL);

    assertThat(reminders).isEmpty();
  }

  @Test
  void stillRemindsAParentWhoseChildAccountLastMovedBeforeTheyOpenedTheSurvey() {
    user(SAVER);
    viewed(SAVER, PERSON_SURVEY, NOW.minus(10, DAYS));
    representedChild(SAVER, CHILD);
    childAccountTouched(CHILD, NOW.minus(20, DAYS));

    var reminders = repository.fetch(VIEWED_FROM, VIEWED_UNTIL);

    assertThat(reminders)
        .extracting(reminder -> reminder.person().getPersonalCode())
        .containsExactly(SAVER);
  }

  @Test
  void remindsSomeoneWhoOnlyVerifiedTheirIdentityToOpenACompanyAccount() {
    var saver = user(SAVER);
    viewed(SAVER, PERSON_SURVEY, NOW.minus(10, DAYS));
    identityVerifiedBy(saver);

    var reminders = repository.fetch(VIEWED_FROM, VIEWED_UNTIL);

    assertThat(reminders)
        .extracting(reminder -> reminder.person().getPersonalCode())
        .containsExactly(SAVER);
  }

  @Test
  void leavesOutAdultsUnderActiveGuardianship() {
    user(SAVER);
    viewed(SAVER, PERSON_SURVEY, NOW.minus(10, DAYS));
    underGuardianship(SAVER, LocalDate.of(2030, 1, 1));

    var reminders = repository.fetch(VIEWED_FROM, VIEWED_UNTIL);

    assertThat(reminders).isEmpty();
  }

  @Test
  void remindsAdultsWhoseGuardianshipHasEnded() {
    user(SAVER);
    viewed(SAVER, PERSON_SURVEY, NOW.minus(10, DAYS));
    underGuardianship(SAVER, LocalDate.of(2026, 10, 1));

    var reminders = repository.fetch(VIEWED_FROM, VIEWED_UNTIL);

    assertThat(reminders)
        .extracting(reminder -> reminder.person().getPersonalCode())
        .containsExactly(SAVER);
  }

  @Test
  void leavesOutMinors() {
    user(MINOR);
    viewed(MINOR, PERSON_SURVEY, NOW.minus(10, DAYS));

    var reminders = repository.fetch(VIEWED_FROM, VIEWED_UNTIL);

    assertThat(reminders).isEmpty();
  }

  @Test
  void neverRemindsAPersonASecondTime() {
    user(SAVER);
    emailSentTo(SAVER, SAVINGS_FUND_ONBOARDING_ABANDONMENT_PERSON, NOW.minus(200, DAYS));
    viewed(SAVER, PERSON_SURVEY, NOW.minus(10, DAYS));

    var reminders = repository.fetch(VIEWED_FROM, VIEWED_UNTIL);

    assertThat(reminders).isEmpty();
  }

  @Test
  void stillRemindsAPersonWhoGotADifferentEmail() {
    user(SAVER);
    emailSentTo(SAVER, SAVINGS_FUND_ONBOARDING_ABANDONMENT_CHILD, NOW.minus(5, DAYS));
    viewed(SAVER, PERSON_SURVEY, NOW.minus(10, DAYS));

    var reminders = repository.fetch(VIEWED_FROM, VIEWED_UNTIL);

    assertThat(reminders)
        .extracting(reminder -> reminder.person().getPersonalCode())
        .containsExactly(SAVER);
  }

  @Test
  void followsTheLanguagePreferenceInTheLatestRegistrySnapshot() {
    user(SAVER);
    user(OTHER_SAVER);
    viewed(SAVER, PERSON_SURVEY, NOW.minus(10, DAYS));
    viewed(OTHER_SAVER, PERSON_SURVEY, NOW.minus(10, DAYS));
    languagePreference(SAVER, "EST", LATEST_SNAPSHOT.minusDays(1));
    languagePreference(SAVER, "ENG", LATEST_SNAPSHOT);
    languagePreference(OTHER_SAVER, "ENG", LATEST_SNAPSHOT.minusDays(1));

    var reminders = repository.fetch(VIEWED_FROM, VIEWED_UNTIL);

    assertThat(reminders)
        .extracting(
            reminder -> reminder.person().getPersonalCode(),
            PersonOnboardingAbandonmentReminder::locale)
        .containsExactly(tuple(SAVER, ENGLISH), tuple(OTHER_SAVER, ESTONIAN));
  }

  @Test
  void leavesOutPeopleWithABlankEmailAddress() {
    entityManager.persist(userBuilder(SAVER).email("").build());
    viewed(SAVER, PERSON_SURVEY, NOW.minus(10, DAYS));

    var reminders = repository.fetch(VIEWED_FROM, VIEWED_UNTIL);

    assertThat(reminders).isEmpty();
  }

  @Test
  void leavesOutPeopleWithoutAnEmailAddress() {
    entityManager.persist(userBuilder(SAVER).email(null).build());
    viewed(SAVER, PERSON_SURVEY, NOW.minus(10, DAYS));

    var reminders = repository.fetch(VIEWED_FROM, VIEWED_UNTIL);

    assertThat(reminders).isEmpty();
  }

  private User user(String personalCode) {
    return entityManager.persist(userBuilder(personalCode).build());
  }

  private User.UserBuilder userBuilder(String personalCode) {
    return User.builder()
        .personalCode(personalCode)
        .firstName("Saver " + personalCode)
        .lastName("Example")
        .email(personalCode + "@example.com")
        .createdDate(NOW)
        .updatedDate(NOW)
        .active(true);
  }

  private void viewed(String personalCode, String path, Instant viewedAt) {
    event(personalCode, "PAGE_VIEW", path, viewedAt);
  }

  private void event(String personalCode, String type, String path, Instant timestamp) {
    jdbcClient
        .sql(
            """
            INSERT INTO event_log (principal, type, data, timestamp)
            VALUES (:principal, :type, CAST(:data AS jsonb), :timestamp)
            """)
        .param("principal", personalCode)
        .param("type", type)
        .param("data", "{\"path\": \"" + path + "\"}")
        .param("timestamp", Timestamp.from(timestamp))
        .update();
  }

  private void surveySubmittedBy(User user) {
    entityManager.persist(
        KycSurvey.builder().userId(user.getId()).survey(new KycSurveyResponse(List.of())).build());
    entityManager.flush();
  }

  private void identityVerifiedBy(User user) {
    entityManager.persist(
        KycSurvey.builder()
            .userId(user.getId())
            .survey(new KycSurveyResponse(List.of(), IDENTITY_ONLY))
            .build());
    entityManager.flush();
  }

  private void surveyWithoutPurposeSubmittedBy(User user) {
    jdbcClient
        .sql(
            """
            INSERT INTO kyc_survey (user_id, survey)
            VALUES (:userId, CAST('{"answers": []}' AS jsonb))
            """)
        .param("userId", user.getId())
        .update();
  }

  private void representedChild(String parentCode, String childCode) {
    parentChildLinkRepository.save(
        ParentChildLink.builder()
            .parentPersonalCode(parentCode)
            .childPersonalCode(childCode)
            .relationshipType(LEGAL_REPRESENTATIVE)
            .status(ACTIVE)
            .validUntil(LocalDate.of(2030, 1, 1))
            .build());
    entityManager.flush();
  }

  private void childAccountTouched(String childCode, Instant touchedAt) {
    onboardingRepository.saveOnboardingStatus(childCode, PERSON, PENDING);
    jdbcClient
        .sql("UPDATE savings_fund_onboarding SET updated_at = :touchedAt WHERE code = :code")
        .param("touchedAt", Timestamp.from(touchedAt))
        .param("code", childCode)
        .update();
  }

  private void underGuardianship(String wardCode, LocalDate validUntil) {
    parentChildLinkRepository.save(
        ParentChildLink.builder()
            .parentPersonalCode(OTHER_SAVER)
            .childPersonalCode(wardCode)
            .relationshipType(GUARDIAN)
            .status(ACTIVE)
            .validUntil(validUntil)
            .build());
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
