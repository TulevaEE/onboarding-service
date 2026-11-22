package ee.tuleva.onboarding.user;

import static ee.tuleva.onboarding.user.ContactChangeSource.SELF_SERVICE;
import static ee.tuleva.onboarding.user.ContactField.EMAIL;
import static ee.tuleva.onboarding.user.ContactField.PHONE;
import static java.time.temporal.ChronoUnit.DAYS;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.user.contact.ContactChangeRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@Import({
  RecentlyReplacedEmails.class,
  ContactChangeRepository.class,
  ContactChangeConfiguration.class
})
@TestPropertySource(properties = "contact-change.replaced-email-notice-window=10d")
class RecentlyReplacedEmailsIT {

  private static final Instant NOW = Instant.parse("2026-11-20T10:00:00Z");
  private static final Duration WINDOW = Duration.ofDays(10);
  private static final Instant SIGNED_AT = NOW.minus(Duration.ofDays(30));

  @Autowired RecentlyReplacedEmails recentlyReplacedEmails;
  @Autowired ContactChangeRepository contactChangeRepository;
  @Autowired TestEntityManager entityManager;
  @Autowired JdbcClient jdbcClient;

  @Test
  void anEmailReplacedInsideTheWindowIsReturned() {
    var user = persistedUser("38812121215", "current@window.example.com");
    recordChange(
        user,
        EMAIL,
        "replaced@window.example.com",
        "current@window.example.com",
        NOW.minus(1, DAYS));

    assertThat(recentlyReplacedEmails.of(user.getIdOrThrow(), NOW))
        .containsExactly("replaced@window.example.com");
  }

  @Test
  void anEmailReplacedExactlyOneWindowAgoIsStillReturned() {
    var user = persistedUser("38812121215", "current@window.example.com");
    recordChange(
        user,
        EMAIL,
        "replaced@window.example.com",
        "current@window.example.com",
        NOW.minus(WINDOW));

    assertThat(recentlyReplacedEmails.of(user.getIdOrThrow(), NOW))
        .containsExactly("replaced@window.example.com");
  }

  @Test
  void anEmailReplacedASecondBeforeTheWindowIsNotReturned() {
    var user = persistedUser("38812121215", "current@window.example.com");
    recordChange(
        user,
        EMAIL,
        "replaced@window.example.com",
        "current@window.example.com",
        NOW.minus(WINDOW).minusSeconds(1));

    assertThat(recentlyReplacedEmails.of(user.getIdOrThrow(), NOW)).isEmpty();
  }

  @Test
  void anEmailReplacedAtTheMomentAskedAboutIsReturned() {
    var user = persistedUser("38812121215", "current@window.example.com");
    recordChange(
        user, EMAIL, "replaced@window.example.com", "current@window.example.com", SIGNED_AT);

    assertThat(recentlyReplacedEmails.of(user.getIdOrThrow(), SIGNED_AT))
        .containsExactly("replaced@window.example.com");
  }

  @Test
  void anEmailReplacedAfterTheMomentAskedAboutIsNotReturned() {
    var user = persistedUser("38812121215", "current@window.example.com");
    recordChange(
        user,
        EMAIL,
        "replaced@window.example.com",
        "current@window.example.com",
        SIGNED_AT.plusSeconds(1));

    assertThat(recentlyReplacedEmails.of(user.getIdOrThrow(), SIGNED_AT)).isEmpty();
  }

  @Test
  void theWindowReachesBackInclusivelyFromTheMomentAskedAboutRatherThanFromNow() {
    var user = persistedUser("38812121215", "current@window.example.com");
    recordChange(
        user,
        EMAIL,
        "too-early@window.example.com",
        "inside@window.example.com",
        SIGNED_AT.minus(WINDOW).minusSeconds(1));
    recordChange(
        user,
        EMAIL,
        "inside@window.example.com",
        "current@window.example.com",
        SIGNED_AT.minus(WINDOW));

    assertThat(recentlyReplacedEmails.of(user.getIdOrThrow(), SIGNED_AT))
        .containsExactly("inside@window.example.com");
  }

  @Test
  void aReplacedPhoneNumberIsNeverReturned() {
    var user = persistedUser("38812121215", "current@window.example.com");
    recordChange(user, PHONE, "+37255555555", "+37251111111", NOW.minus(1, DAYS));

    assertThat(recentlyReplacedEmails.of(user.getIdOrThrow(), NOW)).isEmpty();
  }

  @Test
  void aFirstEmailReplacedNothingSoNothingIsReturned() {
    var user = persistedUser("38812121215", "current@window.example.com");
    recordChange(user, EMAIL, null, "current@window.example.com", NOW.minus(1, DAYS));

    assertThat(recentlyReplacedEmails.of(user.getIdOrThrow(), NOW)).isEmpty();
  }

  @Test
  void anAddressThePersonChangedBackToIsNotReturnedWhateverItsLetterCase() {
    var user = persistedUser("38812121215", "Current@Window.Example.com");
    recordChange(
        user, EMAIL, "current@window.example.com", "other@window.example.com", NOW.minus(2, DAYS));
    recordChange(
        user, EMAIL, "other@window.example.com", "Current@Window.Example.com", NOW.minus(1, DAYS));

    assertThat(recentlyReplacedEmails.of(user.getIdOrThrow(), NOW))
        .containsExactly("other@window.example.com");
  }

  @Test
  void aLookAlikeOfTheAddressThePersonHoldsNowIsAnotherMailboxSoItIsReturned() {
    var user = persistedUser("38812121215", "\u212Aey@window.example.com");
    recordChange(
        user, EMAIL, "key@window.example.com", "\u212Aey@window.example.com", NOW.minus(1, DAYS));

    assertThat(recentlyReplacedEmails.of(user.getIdOrThrow(), NOW))
        .containsExactly("key@window.example.com");
  }

  @Test
  void lookAlikeAddressesAreDifferentMailboxesSoNeitherHidesTheOther() {
    var user = persistedUser("38812121215", "current@window.example.com");
    recordChange(
        user, EMAIL, "key@window.example.com", "\u212Aey@window.example.com", NOW.minus(2, DAYS));
    recordChange(
        user,
        EMAIL,
        "\u212Aey@window.example.com",
        "current@window.example.com",
        NOW.minus(1, DAYS));

    assertThat(recentlyReplacedEmails.of(user.getIdOrThrow(), NOW))
        .containsExactly("\u212Aey@window.example.com", "key@window.example.com");
  }

  @Test
  void theAddressThePersonHoldsNowIsNotReturnedEvenToACallerHoldingAnOutdatedCopyOfThem() {
    var outdatedCopy = persistedUser("38812121215", "other@window.example.com");
    recordChange(
        outdatedCopy,
        EMAIL,
        "stored@window.example.com",
        "other@window.example.com",
        NOW.minus(2, DAYS));
    recordChange(
        outdatedCopy,
        EMAIL,
        "other@window.example.com",
        "stored@window.example.com",
        NOW.minus(1, DAYS));
    storeEmail(outdatedCopy, "stored@window.example.com");

    assertThat(recentlyReplacedEmails.of(outdatedCopy.getIdOrThrow(), NOW))
        .containsExactly("other@window.example.com");
  }

  @Test
  void anAddressAnotherPersonNowHoldsIsStillReturnedSoASecondAccountCannotSilenceTheNotice() {
    var user = persistedUser("38812121215", "current@window.example.com");
    persistedUser("38501010002", "taken@window.example.com");
    recordChange(
        user, EMAIL, "taken@window.example.com", "current@window.example.com", NOW.minus(1, DAYS));

    assertThat(recentlyReplacedEmails.of(user.getIdOrThrow(), NOW))
        .containsExactly("taken@window.example.com");
  }

  @Test
  void anAddressAnotherPersonHoldsInAnotherLetterCaseIsStillReturned() {
    var user = persistedUser("38812121215", "current@window.example.com");
    persistedUser("38501010002", "Taken@Window.Example.com");
    recordChange(
        user, EMAIL, "taken@window.example.com", "current@window.example.com", NOW.minus(1, DAYS));

    assertThat(recentlyReplacedEmails.of(user.getIdOrThrow(), NOW))
        .containsExactly("taken@window.example.com");
  }

  @Test
  void anotherPersonsReplacedEmailsAreNotReturned() {
    var user = persistedUser("38812121215", "current@window.example.com");
    var otherUser = persistedUser("38501010002", "someone@window.example.com");
    recordChange(
        otherUser,
        EMAIL,
        "theirs@window.example.com",
        "someone@window.example.com",
        NOW.minus(1, DAYS));

    assertThat(recentlyReplacedEmails.of(user.getIdOrThrow(), NOW)).isEmpty();
  }

  @Test
  void anAddressReplacedTwiceInDifferentLetterCaseIsReturnedOnceInItsNewestSpelling() {
    var user = persistedUser("38812121215", "current@window.example.com");
    recordChange(
        user, EMAIL, "twice@window.example.com", "other@window.example.com", NOW.minus(3, DAYS));
    recordChange(
        user, EMAIL, "other@window.example.com", "Twice@Window.Example.com", NOW.minus(2, DAYS));
    recordChange(
        user, EMAIL, "Twice@Window.Example.com", "current@window.example.com", NOW.minus(1, DAYS));

    assertThat(recentlyReplacedEmails.of(user.getIdOrThrow(), NOW))
        .containsExactly("Twice@Window.Example.com", "other@window.example.com");
  }

  @Test
  void everyAddressInAChainOfChangesInsideTheWindowIsReturned() {
    var user = persistedUser("38812121215", "third@window.example.com");
    recordChange(
        user, EMAIL, "first@window.example.com", "second@window.example.com", NOW.minus(9, DAYS));
    recordChange(
        user, EMAIL, "second@window.example.com", "third@window.example.com", NOW.minus(1, DAYS));

    assertThat(recentlyReplacedEmails.of(user.getIdOrThrow(), NOW))
        .containsExactlyInAnyOrder("first@window.example.com", "second@window.example.com");
  }

  @Test
  void aLaterChangeDoesNotRestartTheWindowOfAnEarlierReplacedAddress() {
    var user = persistedUser("38812121215", "third@window.example.com");
    recordChange(
        user, EMAIL, "first@window.example.com", "second@window.example.com", NOW.minus(11, DAYS));
    recordChange(
        user, EMAIL, "second@window.example.com", "third@window.example.com", NOW.minus(2, DAYS));

    assertThat(recentlyReplacedEmails.of(user.getIdOrThrow(), NOW))
        .containsExactly("second@window.example.com");
  }

  @Test
  void aPersonWithNoEmailOnFileGetsEveryReplacedAddressInsideTheWindow() {
    var user = persistedUser("38812121215", null);
    recordChange(user, EMAIL, "removed@window.example.com", null, NOW.minus(1, DAYS));

    assertThat(recentlyReplacedEmails.of(user.getIdOrThrow(), NOW))
        .containsExactly("removed@window.example.com");
  }

  private User persistedUser(String personalCode, @Nullable String email) {
    return entityManager.persistAndFlush(
        User.builder()
            .firstName("Test")
            .lastName("Person")
            .personalCode(personalCode)
            .email(email)
            .build());
  }

  private void storeEmail(User user, String email) {
    jdbcClient
        .sql("UPDATE users SET email = :email WHERE id = :userId")
        .param("email", email)
        .param("userId", user.getIdOrThrow())
        .update();
  }

  private void recordChange(
      User user,
      ContactField field,
      @Nullable String oldValue,
      @Nullable String newValue,
      Instant changedAt) {
    contactChangeRepository.saveAll(
        user.getIdOrThrow(),
        SELF_SERVICE,
        List.of(new ContactChange(field, oldValue, newValue, changedAt)));
  }
}
