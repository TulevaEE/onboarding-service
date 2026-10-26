package db.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.flywaydb.core.Flyway;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.support.GeneratedKeyHolder;

class V1_323RemoveIndexGroupLimitsAndRequireIsinTest {

  private static final String URL =
      "jdbc:h2:mem:indexgrouplimitremoval;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE"
          + ";DEFAULT_NULL_ORDERING=HIGH;NON_KEYWORDS=KEY,VALUE;DB_CLOSE_DELAY=-1";
  private static final String[] FLYWAY_LOCATIONS = {
    "classpath:/db/migration", "classpath:/db/dev", "classpath:/db/h2"
  };
  private static final String JUST_BEFORE_THE_REMOVAL_EVEN_IF_NO_MIGRATION_HOLDS_IT = "1.322?";
  private static final String THE_REMOVAL = "1.323";

  private static final String FUND_WITH_GROUPS = "TUK00";
  private static final String OTHER_FUND = "TUK75";
  private static final String GROUP_A = "Group A";
  private static final String GROUP_B = "Group B";
  private static final String MEMBER_ISIN = "ZZ0000000001";
  private static final String PLAIN_ISIN = "ZZ0000000002";

  private static final LocalDate FIRST_VERSION = LocalDate.of(2025, 6, 30);
  private static final LocalDate RETIRED_VERSION = LocalDate.of(2026, 3, 30);

  private SingleConnectionDataSource dataSource;
  private JdbcClient jdbcClient;

  @BeforeEach
  void migrateAFreshDatabaseUpToJustBeforeTheRemoval() {
    dataSource = new SingleConnectionDataSource(URL, "sa", "", true);
    migrateTo(JUST_BEFORE_THE_REMOVAL_EVEN_IF_NO_MIGRATION_HOLDS_IT);
    jdbcClient = JdbcClient.create(dataSource);
  }

  @AfterEach
  void dropTheDatabase() {
    new JdbcTemplate(dataSource).execute("DROP ALL OBJECTS");
    dataSource.destroy();
  }

  @Test
  void everyVersionOfEveryFundsGroupRowsIsDeletedSoNoEarlierVersionComesBackIntoForce() {
    givenGroupLimit(FUND_WITH_GROUPS, GROUP_A, FIRST_VERSION);
    givenGroupLimit(FUND_WITH_GROUPS, GROUP_A, RETIRED_VERSION);
    givenGroupLimit(FUND_WITH_GROUPS, GROUP_B, FIRST_VERSION);
    givenGroupLimit(FUND_WITH_GROUPS, GROUP_B, RETIRED_VERSION);
    givenGroupLimit(OTHER_FUND, GROUP_A, RETIRED_VERSION);

    migrateTo(THE_REMOVAL);

    assertThat(remainingLimitIds()).isEmpty();
  }

  @Test
  void perIsinLimitsAreKeptUnchangedIncludingTheGroupTheyOnceBelongedTo() {
    var formerMember = givenMemberLimit(FUND_WITH_GROUPS, MEMBER_ISIN, GROUP_A, RETIRED_VERSION);
    var plain = givenPlainLimit(FUND_WITH_GROUPS, PLAIN_ISIN, RETIRED_VERSION);
    givenGroupLimit(FUND_WITH_GROUPS, GROUP_A, RETIRED_VERSION);

    migrateTo(THE_REMOVAL);

    assertThat(remainingLimitIds()).containsExactly(formerMember, plain);
    assertThat(groupNameOf(formerMember)).contains(GROUP_A);
  }

  @Test
  void aLimitWithoutAnIsinCanNoLongerBeStored() {
    migrateTo(THE_REMOVAL);

    assertThatThrownBy(() -> givenGroupLimit(FUND_WITH_GROUPS, GROUP_A, RETIRED_VERSION))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private void migrateTo(String version) {
    Flyway.configure()
        .dataSource(dataSource)
        .locations(FLYWAY_LOCATIONS)
        .baselineOnMigrate(true)
        .target(version)
        .load()
        .migrate();
  }

  private long givenGroupLimit(String fundCode, String indexGroup, LocalDate effective) {
    return insertLimit(fundCode, null, indexGroup, effective);
  }

  private long givenMemberLimit(
      String fundCode, String isin, String indexGroup, LocalDate effective) {
    return insertLimit(fundCode, isin, indexGroup, effective);
  }

  private long givenPlainLimit(String fundCode, String isin, LocalDate effective) {
    return insertLimit(fundCode, isin, null, effective);
  }

  private long insertLimit(
      String fundCode, @Nullable String isin, @Nullable String indexGroup, LocalDate effective) {
    var keyHolder = new GeneratedKeyHolder();
    jdbcClient
        .sql(
            """
            INSERT INTO investment_position_limit
              (effective_date, fund_code, isin, label, index_group,
               soft_limit_percent, hard_limit_percent)
            VALUES (:effective, :fundCode, :isin, 'Label', :indexGroup, :soft, :hard)
            """)
        .param("effective", effective)
        .param("fundCode", fundCode)
        .param("isin", isin)
        .param("indexGroup", indexGroup)
        .param("soft", new BigDecimal("10.00000000"))
        .param("hard", new BigDecimal("12.00000000"))
        .update(keyHolder, "id");
    return Objects.requireNonNull(keyHolder.getKey()).longValue();
  }

  private List<Long> remainingLimitIds() {
    return jdbcClient
        .sql("SELECT id FROM investment_position_limit ORDER BY id")
        .query(Long.class)
        .list();
  }

  private Optional<String> groupNameOf(long id) {
    return jdbcClient
        .sql("SELECT index_group FROM investment_position_limit WHERE id = :id")
        .param("id", id)
        .query(String.class)
        .optional();
  }
}
