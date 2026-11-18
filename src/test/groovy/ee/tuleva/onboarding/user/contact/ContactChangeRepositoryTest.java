package ee.tuleva.onboarding.user.contact;

import static ee.tuleva.onboarding.user.ContactChangeSource.SELF_SERVICE;
import static ee.tuleva.onboarding.user.ContactField.EMAIL;
import static ee.tuleva.onboarding.user.ContactField.PHONE;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.user.ContactChange;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.contact.ContactChangeRows.Row;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@DataJpaTest
@Import(ContactChangeRepository.class)
class ContactChangeRepositoryTest {

  @Autowired ContactChangeRepository repository;
  @Autowired TestEntityManager entityManager;
  @Autowired JdbcClient jdbcClient;

  @Test
  void storesEveryChangeOfAUserInOrderWithItsSource() {
    var user = persistedUser("38812121215");
    var changedAt = Instant.parse("2026-11-18T10:00:00Z");

    repository.saveAll(
        user.getId(),
        SELF_SERVICE,
        List.of(
            new ContactChange(EMAIL, null, "new@example.com", changedAt),
            new ContactChange(PHONE, "+37255555555", null, changedAt)));

    assertThat(new ContactChangeRows(jdbcClient).of(user.getId()))
        .containsExactly(
            new Row(EMAIL, null, "new@example.com", SELF_SERVICE, changedAt),
            new Row(PHONE, "+37255555555", null, SELF_SERVICE, changedAt));
  }

  @Test
  void keepsTheChangesOfDifferentUsersApart() {
    var user = persistedUser("38812121215");
    var otherUser = persistedUser("38501010002");
    var changedAt = Instant.parse("2026-11-18T10:00:00Z");

    repository.saveAll(
        otherUser.getId(),
        SELF_SERVICE,
        List.of(new ContactChange(EMAIL, "old@example.com", "new@example.com", changedAt)));

    assertThat(new ContactChangeRows(jdbcClient).of(user.getId())).isEmpty();
    assertThat(new ContactChangeRows(jdbcClient).of(otherUser.getId()))
        .containsExactly(
            new Row(EMAIL, "old@example.com", "new@example.com", SELF_SERVICE, changedAt));
  }

  private User persistedUser(String personalCode) {
    return entityManager.persistAndFlush(
        User.builder().firstName("Test").lastName("Person").personalCode(personalCode).build());
  }
}
