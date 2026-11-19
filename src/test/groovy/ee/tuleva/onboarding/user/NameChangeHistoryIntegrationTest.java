package ee.tuleva.onboarding.user;

import static ee.tuleva.onboarding.auth.GrantType.SMART_ID;
import static ee.tuleva.onboarding.user.NameChangeSource.ADMIN;
import static ee.tuleva.onboarding.user.NameChangeSource.SMART_ID_LOGIN;
import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED;

import ee.tuleva.onboarding.time.ClockConfig;
import ee.tuleva.onboarding.time.ClockHolder;
import ee.tuleva.onboarding.user.contact.ContactChangeRecorder;
import ee.tuleva.onboarding.user.contact.ContactChangeRepository;
import ee.tuleva.onboarding.user.name.NameChangeRecorder;
import ee.tuleva.onboarding.user.name.NameChangeRepository;
import ee.tuleva.onboarding.user.name.NameChangeRows;
import ee.tuleva.onboarding.user.name.NameChangeRows.Row;
import java.time.Clock;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@Transactional(propagation = NOT_SUPPORTED)
@Import({
  UserService.class,
  ContactChangeRecorder.class,
  ContactChangeRepository.class,
  NameChangeRecorder.class,
  NameChangeRepository.class,
  ClockConfig.class
})
class NameChangeHistoryIntegrationTest {

  private static final Instant NOW = Instant.parse("2026-11-19T10:00:00Z");
  private static final String PERSONAL_CODE = "39802077017";

  @Autowired UserService userService;
  @Autowired UserRepository userRepository;
  @Autowired JdbcClient jdbcClient;

  NameChangeRows nameChanges;
  User user;

  record StoredName(String firstName, String lastName) {}

  @BeforeEach
  void setUp() {
    ClockHolder.setClock(Clock.fixed(NOW, UTC));
    nameChanges = new NameChangeRows(jdbcClient);
    user =
        userRepository.save(
            User.builder()
                .firstName("Mari")
                .lastName("Maasikas")
                .personalCode(PERSONAL_CODE)
                .email("mari@example.com")
                .build());
  }

  @AfterEach
  void cleanUp() {
    ClockHolder.setDefaultClock();
    nameChanges.deleteOf(user.getIdOrThrow());
    jdbcClient.sql("DELETE FROM users WHERE id = :userId").param("userId", user.getId()).update();
  }

  @Test
  void aNameChangedAtLoginCommitsTheNewNameWithTheOldOneAndTheLoginItCameFrom() {
    userService.changeName(PERSONAL_CODE, "Mari", "Kask", NameChangeBasis.login(SMART_ID));

    assertThat(storedName()).isEqualTo(new StoredName("Mari", "Kask"));
    assertThat(nameChanges.of(user.getIdOrThrow()))
        .containsExactly(new Row("Mari", "Maasikas", "Mari", "Kask", SMART_ID_LOGIN, null, NOW));
  }

  @Test
  void aNameChangedByAnAdministratorKeepsTheReferenceToTheDocumentItIsBasedOn() {
    userService.changeName(
        PERSONAL_CODE, "Maria", "Maasikas", NameChangeBasis.admin("Court ruling 2-26-1234"));

    assertThat(storedName()).isEqualTo(new StoredName("Maria", "Maasikas"));
    assertThat(nameChanges.of(user.getIdOrThrow()))
        .containsExactly(
            new Row("Mari", "Maasikas", "Maria", "Maasikas", ADMIN, "Court ruling 2-26-1234", NOW));
  }

  @Test
  void theSameNameAgainRecordsNothing() {
    userService.changeName(PERSONAL_CODE, "Mari", "Maasikas", NameChangeBasis.login(SMART_ID));

    assertThat(storedName()).isEqualTo(new StoredName("Mari", "Maasikas"));
    assertThat(nameChanges.of(user.getIdOrThrow())).isEmpty();
  }

  private StoredName storedName() {
    return jdbcClient
        .sql("SELECT first_name, last_name FROM users WHERE id = :userId")
        .param("userId", user.getId())
        .query(
            (rs, rowNum) -> new StoredName(rs.getString("first_name"), rs.getString("last_name")))
        .single();
  }
}
