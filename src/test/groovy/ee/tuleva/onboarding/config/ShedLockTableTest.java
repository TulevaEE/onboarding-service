package ee.tuleva.onboarding.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

class ShedLockTableTest {

  private EmbeddedDatabase database;
  private JdbcClient jdbcClient;

  @BeforeEach
  void setUp() {
    database =
        new EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.H2)
            .addScript("db/migration/V1_127__shedlock_scheduling_job_lock.sql")
            .addScript("db/migration/V1_128__shedlock_extend_name_field.sql")
            .build();
    jdbcClient = JdbcClient.create(database);
  }

  @AfterEach
  void tearDown() {
    database.shutdown();
  }

  @Test
  @DisplayName("supports lock names longer than 64 characters")
  void shedlockTable_supportsLongLockNames() {
    var veryLongName =
        "ee.tuleva.onboarding.analytics.transaction.exchange.snapshot.ExchangeTransactionSnapshotScheduledJob_createDailySnapshots";

    assertThat(veryLongName.length()).isGreaterThan(64);

    var lockUntil = Timestamp.from(Instant.now().plusSeconds(60));
    var lockedAt = Timestamp.from(Instant.now());

    var rowsInserted =
        jdbcClient
            .sql("INSERT INTO shedlock(name, lock_until, locked_at, locked_by) VALUES (?, ?, ?, ?)")
            .params(veryLongName, lockUntil, lockedAt, "test-instance")
            .update();

    assertThat(rowsInserted).isEqualTo(1);

    var count =
        jdbcClient
            .sql("SELECT COUNT(*) FROM shedlock WHERE name = ?")
            .param(veryLongName)
            .query(Integer.class)
            .single();

    assertThat(count).isEqualTo(1);
  }

  @Test
  @DisplayName("supports locked_by values longer than 255 characters")
  void shedlockTable_supportsLongLockedByValues() {
    var longLockedBy =
        "arn:aws:ecs:eu-central-1:123456789012:task/production-cluster/very-long-task-name-"
            + "1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef"
            + "1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef"
            + "1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef";

    assertThat(longLockedBy.length()).isGreaterThan(255);

    var lockUntil = Timestamp.from(Instant.now().plusSeconds(60));
    var lockedAt = Timestamp.from(Instant.now());

    var rowsInserted =
        jdbcClient
            .sql("INSERT INTO shedlock(name, lock_until, locked_at, locked_by) VALUES (?, ?, ?, ?)")
            .params("test-lock", lockUntil, lockedAt, longLockedBy)
            .update();

    assertThat(rowsInserted).isEqualTo(1);

    var retrievedLockedBy =
        jdbcClient
            .sql("SELECT locked_by FROM shedlock WHERE name = ?")
            .param("test-lock")
            .query(String.class)
            .single();

    assertThat(retrievedLockedBy).isEqualTo(longLockedBy);
  }

  @Test
  @DisplayName("supports extremely long values for both name and locked_by fields")
  void shedlockTable_supportsVeryLongNamesAndLockedBy() {
    var veryLongName = "a".repeat(500);
    var veryLongLockedBy = "b".repeat(500);

    var lockUntil = Timestamp.from(Instant.now().plusSeconds(60));
    var lockedAt = Timestamp.from(Instant.now());

    var rowsInserted =
        jdbcClient
            .sql("INSERT INTO shedlock(name, lock_until, locked_at, locked_by) VALUES (?, ?, ?, ?)")
            .params(veryLongName, lockUntil, lockedAt, veryLongLockedBy)
            .update();

    assertThat(rowsInserted).isEqualTo(1);

    var count =
        jdbcClient
            .sql("SELECT COUNT(*) FROM shedlock WHERE name = ?")
            .param(veryLongName)
            .query(Integer.class)
            .single();

    assertThat(count).isEqualTo(1);
  }
}
