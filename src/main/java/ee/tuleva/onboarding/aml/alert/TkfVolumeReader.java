package ee.tuleva.onboarding.aml.alert;

import static ee.tuleva.onboarding.aml.alert.AlertPartyType.LEGAL_ENTITY;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TkfVolumeReader {

  private static final DateTimeFormatter MONTH_KEY = DateTimeFormatter.ofPattern("yyyy-MM");

  private static final String MONTHLY_SQL =
      """
      SELECT
        f.party_code AS personal_id,
        f.party_type AS party_type,
        f.period AS period,
        SUM(CASE WHEN f.direction = 'IN' THEN f.amount ELSE 0 END) AS deposits_month,
        SUM(CASE WHEN f.direction = 'OUT' THEN f.amount ELSE 0 END) AS redemptions_month,
        MAX(CASE WHEN f.direction = 'IN' THEN f.created_at END) AS last_deposit_month,
        MAX(CASE WHEN f.direction = 'OUT' THEN f.created_at END) AS last_redemption_month,
        CASE WHEN crm.personal_id IS NULL THEN false ELSE true END AS present_in_crm,
        (COALESCE(crm.balance_in_third_pillar, false)
          OR COALESCE(crm.balance_in_tuk75, false)
          OR COALESCE(crm.balance_in_tuk00, false)) AS existing_client,
        ov.manual_date AS last_manual_review
      FROM (
        SELECT party_code, CAST(party_type AS varchar) AS party_type, amount, created_at,
               'IN' AS direction, date_trunc('month', created_at) AS period
        FROM saving_fund_payment
        WHERE party_type IN ('PERSON', 'LEGAL_ENTITY')
          AND currency = 'EUR'
          AND status IN ('RECEIVED', 'VERIFIED', 'RESERVED', 'ISSUED', 'PROCESSED')
          AND created_at >= :monthFloor
        UNION ALL
        SELECT p.owner_id AS party_code, CAST(p.party_type AS varchar) AS party_type,
               ABS(e.amount) AS amount, e.created_at, 'OUT' AS direction,
               date_trunc('month', e.created_at) AS period
        FROM ledger.entry e
        JOIN ledger.account a ON a.id = e.account_id
        JOIN ledger.party p ON p.id = a.owner_party_id
        WHERE a.purpose = 'USER_ACCOUNT'
          AND a.name = 'REDEMPTIONS'
          AND a.account_type = 'EXPENSE'
          AND a.asset_type = 'EUR'
          AND e.created_at >= :monthFloor
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
        FROM aml_check WHERE type = 'TKF_RISK_LEVEL_OVERRIDE' GROUP BY personal_code
      ) ov ON ov.personal_code = f.party_code
      GROUP BY f.party_code, f.party_type, f.period, crm.personal_id, crm.balance_in_third_pillar,
               crm.balance_in_tuk75, crm.balance_in_tuk00, ov.manual_date
      """;

  private final JdbcClient jdbcClient;
  private final Clock clock;

  public List<TkfVolumeAggregate> readVolumeAggregates() {
    ZoneId zone = clock.getZone();
    LocalDate today = LocalDate.now(clock);
    Instant monthFloor = today.withDayOfMonth(1).minusMonths(1).atStartOfDay(zone).toInstant();
    return jdbcClient
        .sql(MONTHLY_SQL)
        .param("monthFloor", Timestamp.from(monthFloor))
        .query((rs, rowNum) -> monthlyAggregate(rs, zone))
        .list();
  }

  private TkfVolumeAggregate monthlyAggregate(java.sql.ResultSet rs, ZoneId zone)
      throws java.sql.SQLException {
    Instant period = rs.getTimestamp("period").toInstant();
    AlertPartyType partyType = partyType(rs);
    return new TkfVolumeAggregate(
        rs.getString("personal_id"),
        rs.getBigDecimal("deposits_month"),
        rs.getBigDecimal("redemptions_month"),
        toInstant(rs.getTimestamp("last_deposit_month")),
        toInstant(rs.getTimestamp("last_redemption_month")),
        LocalDate.ofInstant(period, zone).format(MONTH_KEY),
        presentInCrm(rs, partyType),
        existingClient(rs, partyType),
        toInstant(rs.getTimestamp("last_manual_review")),
        partyType);
  }

  private static AlertPartyType partyType(java.sql.ResultSet rs) throws java.sql.SQLException {
    return AlertPartyType.valueOf(rs.getString("party_type"));
  }

  private static boolean presentInCrm(java.sql.ResultSet rs, AlertPartyType partyType)
      throws java.sql.SQLException {
    return partyType == LEGAL_ENTITY || rs.getBoolean("present_in_crm");
  }

  private static boolean existingClient(java.sql.ResultSet rs, AlertPartyType partyType)
      throws java.sql.SQLException {
    return partyType != LEGAL_ENTITY && rs.getBoolean("existing_client");
  }

  private static @Nullable Instant toInstant(@Nullable Timestamp timestamp) {
    return timestamp == null ? null : timestamp.toInstant();
  }
}
