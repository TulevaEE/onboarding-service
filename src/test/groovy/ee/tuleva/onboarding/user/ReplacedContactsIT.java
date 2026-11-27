package ee.tuleva.onboarding.user;

import static ee.tuleva.onboarding.user.ContactChangeSource.SELF_SERVICE;
import static ee.tuleva.onboarding.user.ContactField.EMAIL;
import static ee.tuleva.onboarding.user.ContactField.PHONE;
import static java.time.temporal.ChronoUnit.HOURS;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.user.contact.ContactChangeRepository;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import({ReplacedContacts.class, ContactChangeRepository.class})
class ReplacedContactsIT {

  private static final Instant UNTIL = Instant.parse("2026-11-20T10:00:00Z");
  private static final Instant FROM = UNTIL.minus(72, HOURS);
  private static final Instant BETWEEN = UNTIL.minus(1, HOURS);

  @Autowired ReplacedContacts replacedContacts;
  @Autowired ContactChangeRepository contactChangeRepository;
  @Autowired TestEntityManager entityManager;

  @Test
  void anEmailReplacedBetweenTheTwoMomentsIsAReplacement() {
    var user = persistedUser("38812121215");
    recordChange(user, EMAIL, "old@replaced.example.com", "new@replaced.example.com", BETWEEN);

    assertThat(replacedContacts.anyBetween(user.getIdOrThrow(), FROM, UNTIL)).isTrue();
  }

  @Test
  void aPhoneNumberReplacedBetweenTheTwoMomentsIsAReplacement() {
    var user = persistedUser("38812121215");
    recordChange(user, PHONE, "+37255555555", "+37251111111", BETWEEN);

    assertThat(replacedContacts.anyBetween(user.getIdOrThrow(), FROM, UNTIL)).isTrue();
  }

  @Test
  void aContactRemovedBetweenTheTwoMomentsIsAReplacement() {
    var user = persistedUser("38812121215");
    recordChange(user, PHONE, "+37255555555", null, BETWEEN);

    assertThat(replacedContacts.anyBetween(user.getIdOrThrow(), FROM, UNTIL)).isTrue();
  }

  @Test
  void aFirstEverPhoneNumberReplacedNothing() {
    var user = persistedUser("38812121215");
    recordChange(user, PHONE, null, "+37251111111", BETWEEN);

    assertThat(replacedContacts.anyBetween(user.getIdOrThrow(), FROM, UNTIL)).isFalse();
  }

  @Test
  void anEmailWhoseLetterCaseAloneChangedIsNotAReplacement() {
    var user = persistedUser("38812121215");
    recordChange(user, EMAIL, "same@replaced.example.com", "Same@Replaced.Example.com", BETWEEN);

    assertThat(replacedContacts.anyBetween(user.getIdOrThrow(), FROM, UNTIL)).isFalse();
  }

  @Test
  void aReplacementAtEitherMomentIsBetweenThem() {
    var replacedAtTheStart = persistedUser("38812121215");
    var replacedAtTheEnd = persistedUser("38501010002");
    recordChange(replacedAtTheStart, PHONE, "+37255555555", "+37251111111", FROM);
    recordChange(replacedAtTheEnd, PHONE, "+37255555555", "+37251111111", UNTIL);

    assertThat(replacedContacts.anyBetween(replacedAtTheStart.getIdOrThrow(), FROM, UNTIL))
        .isTrue();
    assertThat(replacedContacts.anyBetween(replacedAtTheEnd.getIdOrThrow(), FROM, UNTIL)).isTrue();
  }

  @Test
  void aReplacementASecondBeforeTheFirstMomentIsNotBetweenThem() {
    var user = persistedUser("38812121215");
    recordChange(user, PHONE, "+37255555555", "+37251111111", FROM.minusSeconds(1));

    assertThat(replacedContacts.anyBetween(user.getIdOrThrow(), FROM, UNTIL)).isFalse();
  }

  @Test
  void aReplacementASecondAfterTheSecondMomentIsNotBetweenThem() {
    var user = persistedUser("38812121215");
    recordChange(user, PHONE, "+37255555555", "+37251111111", UNTIL.plusSeconds(1));

    assertThat(replacedContacts.anyBetween(user.getIdOrThrow(), FROM, UNTIL)).isFalse();
  }

  @Test
  void anotherPersonsReplacementIsNotTheirs() {
    var user = persistedUser("38812121215");
    var otherUser = persistedUser("38501010002");
    recordChange(otherUser, EMAIL, "old@replaced.example.com", "new@replaced.example.com", BETWEEN);

    assertThat(replacedContacts.anyBetween(user.getIdOrThrow(), FROM, UNTIL)).isFalse();
  }

  private User persistedUser(String personalCode) {
    return entityManager.persistAndFlush(
        User.builder().firstName("Test").lastName("Person").personalCode(personalCode).build());
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
