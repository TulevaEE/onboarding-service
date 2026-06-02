package ee.tuleva.onboarding.aml.alert;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Reads per-person TKF volume aggregates for the current calendar month and year, mirroring the
 * volume CTEs of T1_ulevaatamist_vajavad.sql: deposits from saving_fund_payment (PERSON, EUR,
 * effected statuses) and redemptions from ledger REDEMPTIONS entries whose transaction touches a
 * TKF100 system account. Window boundaries come from the injected Clock (Europe/Tallinn zone), not
 * the database clock. The TkfVolumeEvaluator applies the thresholds and the manual-override
 * suppression.
 */
@Service
@RequiredArgsConstructor
public class TkfVolumeReader {

  private static final DateTimeFormatter MONTH_KEY = DateTimeFormatter.ofPattern("yyyy-MM");

  private static final String SQL =
      """
      SELECT
        f.party_code AS personal_id,
        SUM(CASE WHEN f.direction = 'IN' AND f.created_at >= :monthStart THEN f.amount ELSE 0 END) AS deposits_month,
        SUM(CASE WHEN f.direction = 'OUT' AND f.created_at >= :monthStart THEN f.amount ELSE 0 END) AS redemptions_month,
        MAX(CASE WHEN f.created_at >= :monthStart THEN f.created_at END) AS last_flow_month,
        SUM(CASE WHEN f.direction = 'IN' THEN f.amount ELSE 0 END) AS deposits_year,
        MAX(CASE WHEN f.direction = 'IN' THEN f.created_at END) AS last_deposit_year,
        CASE WHEN crm.personal_id IS NULL THEN false ELSE true END AS present_in_crm,
        (COALESCE(crm.balance_in_third_pillar, false)
          OR COALESCE(crm.balance_in_tuk75, false)
          OR COALESCE(crm.balance_in_tuk00, false)) AS existing_client,
        ov.manual_date AS last_manual_review
      FROM (
        SELECT party_code, amount, created_at, 'IN' AS direction
        FROM saving_fund_payment
        WHERE party_type = 'PERSON'
          AND currency = 'EUR'
          AND status IN ('RECEIVED', 'VERIFIED', 'RESERVED', 'ISSUED', 'PROCESSED')
          AND created_at >= :yearStart
        UNION ALL
        SELECT p.owner_id AS party_code, ABS(e.amount) AS amount, e.created_at, 'OUT' AS direction
        FROM ledger.entry e
        JOIN ledger.account a ON a.id = e.account_id
        JOIN ledger.party p ON p.id = a.owner_party_id
        WHERE p.party_type = 'PERSON'
          AND a.purpose = 'USER_ACCOUNT'
          AND a.name = 'REDEMPTIONS'
          AND a.account_type = 'EXPENSE'
          AND a.asset_type = 'EUR'
          AND e.created_at >= :monthStart
          AND EXISTS (
            SELECT 1 FROM ledger.entry e2
            JOIN ledger.account a2 ON a2.id = e2.account_id
            WHERE e2.transaction_id = e.transaction_id
              AND a2.purpose = 'SYSTEM_ACCOUNT'
              AND a2.name LIKE '%TKF100%')
      ) f
      LEFT JOIN analytics.mv_crm crm ON crm.personal_id = f.party_code
      LEFT JOIN (
        SELECT personal_code, MAX(created_time) AS manual_date
        FROM aml_check
        WHERE type = 'TKF_RISK_LEVEL_OVERRIDE'
        GROUP BY personal_code
      ) ov ON ov.personal_code = f.party_code
      GROUP BY f.party_code, crm.personal_id, crm.balance_in_third_pillar,
               crm.balance_in_tuk75, crm.balance_in_tuk00, ov.manual_date
      """;

  private final JdbcClient jdbcClient;
  private final Clock clock;

  public List<TkfVolumeAggregate> readVolumeAggregates() {
    ZoneId zone = clock.getZone();
    LocalDate today = LocalDate.now(clock);
    Instant monthStart = today.withDayOfMonth(1).atStartOfDay(zone).toInstant();
    Instant yearStart = today.withDayOfYear(1).atStartOfDay(zone).toInstant();
    String monthKey = today.format(MONTH_KEY);
    String yearKey = String.valueOf(today.getYear());

    return jdbcClient
        .sql(SQL)
        .param("monthStart", Timestamp.from(monthStart))
        .param("yearStart", Timestamp.from(yearStart))
        .query(
            (rs, rowNum) ->
                new TkfVolumeAggregate(
                    rs.getString("personal_id"),
                    rs.getBigDecimal("deposits_month"),
                    rs.getBigDecimal("redemptions_month"),
                    toInstant(rs.getTimestamp("last_flow_month")),
                    monthKey,
                    rs.getBigDecimal("deposits_year"),
                    toInstant(rs.getTimestamp("last_deposit_year")),
                    yearKey,
                    rs.getBoolean("present_in_crm"),
                    rs.getBoolean("existing_client"),
                    toInstant(rs.getTimestamp("last_manual_review"))))
        .list();
  }

  private static Instant toInstant(Timestamp timestamp) {
    return timestamp == null ? null : timestamp.toInstant();
  }
}
