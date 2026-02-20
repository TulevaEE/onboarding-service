package ee.tuleva.onboarding.analytics.thirdpillar;

import static java.time.Clock.systemUTC;
import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class AnalyticsRecentThirdPillarRepositoryTest {

  @Autowired private AnalyticsRecentThirdPillarRepository repository;

  @Autowired private EntityManager entityManager;

  @Autowired private DataSource dataSource;

  private static final String CREATE_ANALYTICS_SCHEMA = "CREATE SCHEMA IF NOT EXISTS \"analytics\"";
  private static final String CREATE_UNIT_OWNER_TABLE =
      """
            CREATE TABLE IF NOT EXISTS "analytics"."unit_owner" (
                "id" BIGINT PRIMARY KEY,
                "personal_id" VARCHAR(255),
                "first_name" VARCHAR(255),
                "last_name" VARCHAR(255),
                "phone" VARCHAR(255),
                "email" VARCHAR(255),
                "country" VARCHAR(255),
                "language_preference" VARCHAR(255),
                "pension_account" VARCHAR(255),
                "p2_choice" VARCHAR(255),
                "death_date" DATE,
                "snapshot_date" DATE,
                "date_created" TIMESTAMP,
                "p3_identification_date" DATE,
                "p3_identifier" VARCHAR(255),
                "p3_block_flag" VARCHAR(255),
                "p3_blocker" VARCHAR(255)
            );
            """;
  private static final String CREATE_UNIT_OWNER_BALANCE_TABLE =
      """
            CREATE TABLE IF NOT EXISTS "analytics"."unit_owner_balance" (
                "id" BIGINT PRIMARY KEY,
                "unit_owner_id" BIGINT,
                "balance_amount" NUMERIC,
                "last_updated" TIMESTAMP,
                "security_short_name" VARCHAR(255),
                "start_date" DATE,
                FOREIGN KEY ("unit_owner_id") REFERENCES "analytics"."unit_owner"("id")
            );
            """;
  private static final String CREATE_BASE_VIEW_H2 =
      """
            CREATE OR REPLACE VIEW "analytics"."v_third_pillar_api_weekly" AS
            SELECT o."id",
                   o."personal_id",
                   o."first_name",
                   o."last_name",
                   o."phone" AS "phone_no",
                   o."email",
                   o."country",
                   CAST(NULL AS VARCHAR) AS "entry_type",
                   o."language_preference" AS "language",
                   o."pension_account" AS "account_no",
                   b."balance_amount" AS "share_amount",
                   b."last_updated" AS "latest_balance_date",
                   CASE WHEN o."p2_choice" IN ('TUK00', 'TUK75') THEN 'A' ELSE NULL END AS "active",
                   o."death_date",
                   o."snapshot_date" AS "reporting_date",
                   o."date_created",
                   'EE3600001707' AS "isin",
                   b."start_date" AS "first_contribution_date",
                   o."p3_identification_date" AS "first_identified_date",
                   o."p3_identifier" AS "first_identified_by",
                   o."p3_block_flag" AS "sanctioned_blocked",
                   o."p3_blocker" AS "blocked_by"
            FROM "analytics"."unit_owner" o
            LEFT JOIN "analytics"."unit_owner_balance" b ON o."id" = b."unit_owner_id"
            WHERE b."security_short_name" = 'TUV100'
            """;
  private static final String CREATE_RECENT_VIEW_H2 =
      """
            CREATE OR REPLACE VIEW "analytics"."v_third_pillar_api_weekly_recent" AS
            SELECT * FROM "analytics"."v_third_pillar_api_weekly"
            WHERE "reporting_date" = (
                SELECT MAX("reporting_date")
                FROM "analytics"."v_third_pillar_api_weekly"
            )
            """;

  @BeforeAll
  static void setupDatabase(@Autowired DataSource ds) throws Exception {
    try (Connection conn = ds.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute(CREATE_ANALYTICS_SCHEMA);
      stmt.execute(CREATE_UNIT_OWNER_TABLE);
      stmt.execute(CREATE_UNIT_OWNER_BALANCE_TABLE);
      stmt.execute(CREATE_BASE_VIEW_H2);
      stmt.execute(CREATE_RECENT_VIEW_H2);
    }
  }

  @AfterAll
  static void tearDownDatabase(@Autowired DataSource ds) throws Exception {
    try (Connection conn = ds.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute("DROP VIEW IF EXISTS \"analytics\".\"v_third_pillar_api_weekly_recent\"");
      stmt.execute("DROP VIEW IF EXISTS \"analytics\".\"v_third_pillar_api_weekly\"");
      stmt.execute("DROP TABLE IF EXISTS \"analytics\".\"unit_owner_balance\"");
      stmt.execute("DROP TABLE IF EXISTS \"analytics\".\"unit_owner\"");
    }
  }

  @BeforeEach
  void cleanUpData() throws Exception {
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute("DELETE FROM analytics.unit_owner_balance;");
      stmt.execute("DELETE FROM analytics.unit_owner;");
    }
  }

  @Test
  @DisplayName("findAll fetches only records from the most recent reporting date")
  void findAll_fetchesOnlyRecentRecordsFromView() {
    // given
    // Record 1: Latest date, should be in the view
    insertUnitOwner(1L, "11111", "John", "Doe", LocalDate.of(2025, 6, 27));
    insertUnitOwnerBalance(1L, 1L, new BigDecimal("1000.00"), "TUV100");

    // Record 2: Latest date, should be in the view
    insertUnitOwner(2L, "22222", "Jane", "Smith", LocalDate.of(2025, 6, 27));
    insertUnitOwnerBalance(2L, 2L, new BigDecimal("2500.50"), "TUV100");

    // Record 3: Should NOT be in the view (different security_short_name)
    insertUnitOwner(3L, "33333", "Peter", "Jones", LocalDate.of(2025, 6, 27));
    insertUnitOwnerBalance(3L, 3L, new BigDecimal("500.00"), "OTHER_FUND");

    // Record 4: Older date, should NOT be in the recent view
    insertUnitOwner(4L, "44444", "Mary", "Anne", LocalDate.of(2025, 6, 20));
    insertUnitOwnerBalance(4L, 4L, new BigDecimal("1234.56"), "TUV100");

    // when
    List<AnalyticsRecentThirdPillar> results = repository.findAll();

    // then
    assertThat(results).hasSize(2);
    assertThat(results)
        .extracting(AnalyticsRecentThirdPillar::getPersonalCode)
        .containsExactlyInAnyOrder("11111", "22222");

    AnalyticsRecentThirdPillar johnDoe =
        results.stream().filter(r -> r.getPersonalCode().equals("11111")).findFirst().orElseThrow();
    assertThat(johnDoe.getFirstName()).isEqualTo("John");
    assertThat(johnDoe.getLastName()).isEqualTo("Doe");
    assertThat(johnDoe.getShareAmount()).isEqualByComparingTo("1000.00");
    assertThat(johnDoe.getReportingDate()).isEqualTo(LocalDate.of(2025, 6, 27));
    assertThat(johnDoe.getIsin()).isEqualTo("EE3600001707");
  }

  @Test
  @DisplayName("findAll returns an empty list when no matching records exist in the view")
  void findAll_returnsEmptyList_whenNoMatchingRecords() {
    // given
    // Insert records that do not match the view's WHERE clause
    insertUnitOwner(1L, "11111", "John", "Doe", LocalDate.of(2025, 6, 27));
    insertUnitOwnerBalance(1L, 1L, new BigDecimal("1000.00"), "NOT_TUV100");

    insertUnitOwner(2L, "22222", "Jane", "Smith", LocalDate.of(2025, 6, 27));
    insertUnitOwnerBalance(2L, 2L, new BigDecimal("2500.50"), "ANOTHER_FUND");

    // when
    List<AnalyticsRecentThirdPillar> results = repository.findAll();

    // then
    assertThat(results).isEmpty();
  }

  private void insertUnitOwner(
      Long id, String personalId, String firstName, String lastName, LocalDate snapshotDate) {
    entityManager
        .createNativeQuery(
            """
            INSERT INTO "analytics"."unit_owner" ("id", "personal_id", "first_name", "last_name", "snapshot_date", "date_created")
            VALUES (?, ?, ?, ?, ?, ?)
            """)
        .setParameter(1, id)
        .setParameter(2, personalId)
        .setParameter(3, firstName)
        .setParameter(4, lastName)
        .setParameter(5, snapshotDate)
        .setParameter(6, LocalDateTime.now(systemUTC()))
        .executeUpdate();
  }

  private void insertUnitOwnerBalance(
      Long id, Long ownerId, BigDecimal balance, String securityShortName) {
    entityManager
        .createNativeQuery(
            """
            INSERT INTO "analytics"."unit_owner_balance" ("id", "unit_owner_id", "balance_amount", "security_short_name", "last_updated")
            VALUES (?, ?, ?, ?, ?)
            """)
        .setParameter(1, id)
        .setParameter(2, ownerId)
        .setParameter(3, balance)
        .setParameter(4, securityShortName)
        .setParameter(5, LocalDateTime.now(systemUTC()))
        .executeUpdate();
  }
}
