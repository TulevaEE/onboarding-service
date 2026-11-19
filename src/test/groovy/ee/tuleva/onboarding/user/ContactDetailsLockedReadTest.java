package ee.tuleva.onboarding.user;

import static ee.tuleva.onboarding.user.ContactChangeSource.SELF_SERVICE;
import static ee.tuleva.onboarding.user.ContactField.EMAIL;
import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.time.ClockConfig;
import ee.tuleva.onboarding.time.ClockHolder;
import ee.tuleva.onboarding.user.contact.ContactChangeRecorder;
import ee.tuleva.onboarding.user.contact.ContactChangeRepository;
import ee.tuleva.onboarding.user.contact.ContactChangeRows;
import ee.tuleva.onboarding.user.contact.ContactChangeRows.Row;
import ee.tuleva.onboarding.user.name.NameChangeRecorder;
import ee.tuleva.onboarding.user.name.NameChangeRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
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
  UserService.class,
  ContactChangeRecorder.class,
  ContactChangeRepository.class,
  NameChangeRecorder.class,
  NameChangeRepository.class,
  ClockConfig.class
})
class ContactDetailsLockedReadTest {

  private static final Instant NOW = Instant.parse("2026-11-18T10:00:00Z");
  private static final String PERSONAL_CODE = "38812121215";

  @Autowired UserService userService;
  @Autowired TestEntityManager entityManager;
  @Autowired JdbcClient jdbcClient;

  @BeforeEach
  void fixTheClock() {
    ClockHolder.setClock(Clock.fixed(NOW, UTC));
  }

  @AfterEach
  void restoreTheClock() {
    ClockHolder.setDefaultClock();
  }

  @Test
  void fillingInLeavesContactDetailsThatAnotherSessionCommittedAfterTheUserWasRead() {
    var user = managedUser(null, null);
    commitEmailBehindTheSession(user, "concurrent@example.com");

    var filledUser =
        userService.fillMissingContactDetails(
            PERSONAL_CODE, Optional.of("registry@example.com"), "+37255555555");

    assertThat(filledUser.getEmail()).isEqualTo("concurrent@example.com");
    assertThat(filledUser.getPhoneNumber()).isNull();
  }

  @Test
  void changingRecordsTheEmailThatAnotherSessionCommittedAfterTheUserWasReadAsTheOldValue() {
    var user = managedUser("old@example.com", null);
    commitEmailBehindTheSession(user, "concurrent@example.com");

    userService.changeContactDetails(PERSONAL_CODE, "new@example.com", null);

    assertThat(new ContactChangeRows(jdbcClient).of(user.getId()))
        .containsExactly(
            new Row(EMAIL, "concurrent@example.com", "new@example.com", SELF_SERVICE, NOW));
  }

  private User managedUser(@Nullable String email, @Nullable String phoneNumber) {
    return entityManager.persistAndFlush(
        User.builder()
            .firstName("Test")
            .lastName("Person")
            .personalCode(PERSONAL_CODE)
            .email(email)
            .phoneNumber(phoneNumber)
            .build());
  }

  private void commitEmailBehindTheSession(User user, String email) {
    jdbcClient
        .sql("UPDATE users SET email = :email WHERE id = :userId")
        .param("email", email)
        .param("userId", user.getId())
        .update();
  }
}
