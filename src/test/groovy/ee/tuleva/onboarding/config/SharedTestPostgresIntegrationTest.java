package ee.tuleva.onboarding.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import ee.tuleva.onboarding.config.SharedTestPostgres.TestDatabase;
import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

@SpringBootTest
class SharedTestPostgresIntegrationTest {

  private static final String POSTGRES_ONLY =
      "Per-context databases are a PostgreSQL-only mechanism";

  @Autowired Environment environment;
  @Autowired DataSource dataSource;

  @Test
  void createDatabaseHandsOutSeparateFullyMigratedDatabases() throws SQLException {
    assumeTrue(isPostgres(), POSTGRES_ONLY);

    TestDatabase first = SharedTestPostgres.createDatabase(environment);
    TestDatabase second = SharedTestPostgres.createDatabase(environment);

    assertThat(databaseNameOf(first)).isNotEqualTo(databaseNameOf(second));
    assertThat(appliedMigrationCount(first))
        .isEqualTo(appliedMigrationCount(second))
        .isGreaterThan(200);
    assertThat(migrationTypes(first)).containsExactlyInAnyOrder("SQL", "JDBC");
    assertThat(tableNames(first))
        .contains("flyway_schema_history", "users", "long_running_transaction_alert");
    assertThat(tableNames(second))
        .contains("flyway_schema_history", "users", "long_running_transaction_alert");
  }

  @Test
  void databasesHandedOutDoNotShareCommittedRows() throws SQLException {
    assumeTrue(isPostgres(), POSTGRES_ONLY);

    TestDatabase first = SharedTestPostgres.createDatabase(environment);
    TestDatabase second = SharedTestPostgres.createDatabase(environment);

    insertAlert(first, 424242);

    assertThat(alertPids(first)).containsExactly(424242);
    assertThat(alertPids(second)).isEmpty();
  }

  @Test
  void springContextRunsAgainstItsOwnClonedDatabase() throws SQLException {
    assumeTrue(isPostgres(), POSTGRES_ONLY);

    assertThat(currentDatabase()).matches("onboarding_test_w\\d+_\\d+");
  }

  private boolean isPostgres() throws SQLException {
    try (Connection connection = dataSource.getConnection()) {
      return connection.getMetaData().getDatabaseProductName().toLowerCase().contains("postgresql");
    }
  }

  private String currentDatabase() throws SQLException {
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement();
        ResultSet resultSet = statement.executeQuery("SELECT current_database()")) {
      resultSet.next();
      return resultSet.getString(1);
    }
  }

  private String databaseNameOf(TestDatabase database) {
    return URI.create(database.url().substring("jdbc:".length())).getPath().substring(1);
  }

  private Connection connectionTo(TestDatabase database) throws SQLException {
    return DriverManager.getConnection(database.url(), database.username(), database.password());
  }

  private long appliedMigrationCount(TestDatabase database) throws SQLException {
    try (Connection connection = connectionTo(database);
        Statement statement = connection.createStatement();
        ResultSet resultSet =
            statement.executeQuery("SELECT count(*) FROM flyway_schema_history WHERE success")) {
      resultSet.next();
      return resultSet.getLong(1);
    }
  }

  private List<String> migrationTypes(TestDatabase database) throws SQLException {
    return queryStrings(database, "SELECT DISTINCT type FROM flyway_schema_history WHERE success");
  }

  private List<String> tableNames(TestDatabase database) throws SQLException {
    return queryStrings(
        database, "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'");
  }

  private List<String> queryStrings(TestDatabase database, String sql) throws SQLException {
    try (Connection connection = connectionTo(database);
        Statement statement = connection.createStatement();
        ResultSet resultSet = statement.executeQuery(sql)) {
      List<String> values = new ArrayList<>();
      while (resultSet.next()) {
        values.add(resultSet.getString(1));
      }
      return values;
    }
  }

  private void insertAlert(TestDatabase database, int pid) throws SQLException {
    try (Connection connection = connectionTo(database);
        Statement statement = connection.createStatement()) {
      statement.executeUpdate(
          "INSERT INTO long_running_transaction_alert (pid, xact_start, query, created_time)"
              + " VALUES ("
              + pid
              + ", now(), 'probe', now())");
    }
  }

  private List<Integer> alertPids(TestDatabase database) throws SQLException {
    try (Connection connection = connectionTo(database);
        Statement statement = connection.createStatement();
        ResultSet resultSet =
            statement.executeQuery("SELECT pid FROM long_running_transaction_alert")) {
      List<Integer> pids = new ArrayList<>();
      while (resultSet.next()) {
        pids.add(resultSet.getInt(1));
      }
      return pids;
    }
  }
}
