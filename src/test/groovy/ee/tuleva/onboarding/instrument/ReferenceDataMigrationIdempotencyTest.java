package ee.tuleva.onboarding.instrument;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

class ReferenceDataMigrationIdempotencyTest {

  private static final String MIGRATION_SCRIPT =
      "db/migration/V1_249__constrain_instrument_reference.sql";
  private static final String URL =
      "jdbc:h2:mem:referencedatamigration;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE"
          + ";DEFAULT_NULL_ORDERING=HIGH;NON_KEYWORDS=KEY,VALUE;DB_CLOSE_DELAY=-1";

  private static final String GLOBAL_BOND_PROXY_AFTER_REPOINTING = "LU1708330318";

  private SingleConnectionDataSource dataSource;
  private JdbcClient jdbcClient;

  // One connection throughout: H2 rejects every row a table's check constraints guard once the
  // session that created the table has closed.
  @BeforeEach
  void migrateAFreshDatabase() {
    dataSource = new SingleConnectionDataSource(URL, "sa", "", true);
    Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:/db/migration", "classpath:/db/dev", "classpath:/db/h2")
        .baselineOnMigrate(true)
        .load()
        .migrate();
    jdbcClient = JdbcClient.create(dataSource);
  }

  @AfterEach
  void dropTheDatabase() {
    new JdbcTemplate(dataSource).execute("DROP ALL OBJECTS");
    dataSource.destroy();
  }

  @Test
  void rerunningTheMigrationKeepsABenchmarkProxyThatHasSinceBeenRePointed() throws SQLException {
    rePointGlobalBondProxy();

    rerunMigration();

    assertThat(proxies())
        .containsExactly(
            new ProxyRow("BOND_EURO", "IE00B3DKXQ41", "IE00B3DKXQ41", null),
            new ProxyRow(
                "BOND_GLOBAL",
                GLOBAL_BOND_PROXY_AFTER_REPOINTING,
                GLOBAL_BOND_PROXY_AFTER_REPOINTING,
                null),
            new ProxyRow("EQUITY_DM", "IE00B4L5Y983", null, "MSCI_WORLD"),
            new ProxyRow("EQUITY_EM", "IE00B4L5YC18", null, "MSCI_EM"));
  }

  @Test
  void rerunningTheMigrationLeavesTheProxyTableInTheSameShape() throws SQLException {
    var columnsAfterOneRun = proxyColumns();

    rerunMigration();

    assertThat(proxyColumns())
        .isEqualTo(columnsAfterOneRun)
        .containsExactly(
            "benchmark_category", "etf_proxy_isin", "id", "index_proxy_isin", "index_series_key");
  }

  @Test
  void rerunningTheMigrationKeepsTheRecordedHistory() throws SQLException {
    jdbcClient
        .sql(
            "INSERT INTO reference_data_history"
                + " (table_name, record_key, operation, old_values, new_values, changed_by, changed_at)"
                + " VALUES ('benchmark_category_proxy', 'BOND_GLOBAL', 'UPDATE', NULL, NULL, 'ops-console', now())")
        .update();

    rerunMigration();

    assertThat(
            jdbcClient
                .sql("SELECT record_key FROM reference_data_history")
                .query(String.class)
                .list())
        .containsExactly("BOND_GLOBAL");
  }

  private void rePointGlobalBondProxy() {
    jdbcClient
        .sql(
            "UPDATE benchmark_category_proxy SET etf_proxy_isin = :isin, index_proxy_isin = :isin"
                + " WHERE benchmark_category = 'BOND_GLOBAL'")
        .param("isin", GLOBAL_BOND_PROXY_AFTER_REPOINTING)
        .update();
  }

  private void rerunMigration() throws SQLException {
    try (var connection = dataSource.getConnection()) {
      ScriptUtils.executeSqlScript(connection, new ClassPathResource(MIGRATION_SCRIPT));
    }
  }

  private List<ProxyRow> proxies() {
    return jdbcClient
        .sql(
            "SELECT benchmark_category, etf_proxy_isin, index_proxy_isin, index_series_key"
                + " FROM benchmark_category_proxy ORDER BY benchmark_category")
        .query(
            (rs, rowNum) ->
                new ProxyRow(
                    rs.getString("benchmark_category"),
                    rs.getString("etf_proxy_isin"),
                    rs.getString("index_proxy_isin"),
                    rs.getString("index_series_key")))
        .list();
  }

  private List<String> proxyColumns() {
    return jdbcClient
        .sql(
            "SELECT lower(column_name) FROM information_schema.columns"
                + " WHERE lower(table_name) = 'benchmark_category_proxy' ORDER BY lower(column_name)")
        .query(String.class)
        .list();
  }

  private record ProxyRow(
      String benchmarkCategory,
      String etfProxyIsin,
      @Nullable String indexProxyIsin,
      @Nullable String indexSeriesKey) {}
}
