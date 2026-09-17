package ee.tuleva.onboarding.investment.fees.ocf;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
@RequiredArgsConstructor
public class OcfSnapshotRepository {

  private final JdbcClient jdbcClient;

  public void save(OcfSnapshot snapshot) {
    if (updateWorkingVersion(snapshot) > 0) {
      return;
    }
    try {
      insertNextVersion(snapshot);
    } catch (DuplicateKeyException e) {
      // Two calculations for the same fund and month read the same MAX(version) and picked the same
      // next number; the unique constraint let exactly one through. The winner's row is now the
      // working version, so the loser writes into it instead of racing for a number again — both
      // runs computed the same month, so overwriting is the intended outcome, not a lost update.
      if (updateWorkingVersion(snapshot) == 0) {
        throw e;
      }
      log.info(
          "Concurrent OCF snapshot insert for fund={}, month={}; wrote into the winning version",
          snapshot.fundCode(),
          snapshot.snapshotMonth());
    }
  }

  private int updateWorkingVersion(OcfSnapshot snapshot) {
    return bindValues(
            jdbcClient.sql(
                """
                UPDATE investment_ocf_snapshot SET
                  management_fee_rate = :managementFeeRate,
                  depot_fee_rate = :depotFeeRate,
                  underlying_fund_cost = :underlyingFundCost,
                  transaction_cost_rate = :transactionCostRate,
                  total_ocf = :totalOcf,
                  complete = :complete,
                  checks = :checks,
                  nav_date = :navDate,
                  nav_calculation_id = :navCalculationId,
                  assets_under_management = :assetsUnderManagement,
                  management_fee_rate_id = :managementFeeRateId,
                  depot_charged_to_fund = :depotChargedToFund,
                  depot_tier_nav_date = :depotTierNavDate,
                  depot_tier_basis = :depotTierBasis,
                  txn_window_start = :txnWindowStart,
                  txn_window_end = :txnWindowEnd,
                  txn_commissions = :txnCommissions,
                  txn_average_aum = :txnAverageAum,
                  txn_nav_dates = :txnNavDates,
                  calculated_at = now()
                WHERE fund_code = :fundCode
                  AND snapshot_month = :snapshotMonth
                  AND published_at IS NULL
                """),
            snapshot)
        .update();
  }

  private void insertNextVersion(OcfSnapshot snapshot) {
    bindValues(
            jdbcClient.sql(
                """
                INSERT INTO investment_ocf_snapshot
                  (fund_code, snapshot_month, version, management_fee_rate, depot_fee_rate,
                   underlying_fund_cost, transaction_cost_rate, total_ocf, complete, checks,
                   nav_date, nav_calculation_id, assets_under_management, management_fee_rate_id,
                   depot_charged_to_fund, depot_tier_nav_date, depot_tier_basis,
                   txn_window_start, txn_window_end, txn_commissions, txn_average_aum,
                   txn_nav_dates)
                SELECT :fundCode, :snapshotMonth, COALESCE(MAX(version), 0) + 1,
                       :managementFeeRate, :depotFeeRate, :underlyingFundCost,
                       :transactionCostRate, :totalOcf, :complete, :checks,
                       :navDate, :navCalculationId, :assetsUnderManagement, :managementFeeRateId,
                       :depotChargedToFund, :depotTierNavDate, :depotTierBasis,
                       :txnWindowStart, :txnWindowEnd, :txnCommissions, :txnAverageAum,
                       :txnNavDates
                FROM investment_ocf_snapshot
                WHERE fund_code = :fundCode AND snapshot_month = :snapshotMonth
                """),
            snapshot)
        .update();
  }

  private StatementSpec bindValues(StatementSpec spec, OcfSnapshot snapshot) {
    var audit = snapshot.audit();
    return spec.param("fundCode", snapshot.fundCode())
        .param("snapshotMonth", snapshot.snapshotMonth())
        .param("managementFeeRate", snapshot.managementFeeRate())
        .param("depotFeeRate", snapshot.depotFeeRate())
        .param("underlyingFundCost", snapshot.underlyingFundCost())
        .param("transactionCostRate", snapshot.transactionCostRate())
        .param("totalOcf", snapshot.totalOcf())
        .param("complete", snapshot.complete())
        .param("checks", snapshot.checks() == null ? "{}" : snapshot.checks())
        .param("navDate", audit.navDate())
        .param("navCalculationId", audit.navCalculationId())
        .param("assetsUnderManagement", audit.assetsUnderManagement())
        .param("managementFeeRateId", audit.managementFeeRateId())
        .param("depotChargedToFund", audit.depotChargedToFund())
        .param("depotTierNavDate", audit.depotTierNavDate())
        .param("depotTierBasis", audit.depotTierBasis())
        .param("txnWindowStart", audit.txnWindowStart())
        .param("txnWindowEnd", audit.txnWindowEnd())
        .param("txnCommissions", audit.txnCommissions())
        .param("txnAverageAum", audit.txnAverageAum())
        .param("txnNavDates", audit.txnNavDates());
  }

  /**
   * Marks the working version as published. Returns false when there was nothing to publish — no
   * snapshot for that month, or its latest version has already gone out somewhere.
   */
  public boolean publish(String fundCode, LocalDate snapshotMonth, String publishedIn) {
    var published =
        jdbcClient
            .sql(
                """
                UPDATE investment_ocf_snapshot
                SET published_at = CURRENT_TIMESTAMP, published_in = :publishedIn
                WHERE fund_code = :fundCode
                  AND snapshot_month = :snapshotMonth
                  AND published_at IS NULL
                """)
            .param("fundCode", fundCode)
            .param("snapshotMonth", snapshotMonth)
            .param("publishedIn", publishedIn)
            .update();
    if (published == 0) {
      log.warn(
          "Nothing to publish: fund={}, month={} has no unpublished snapshot",
          fundCode,
          snapshotMonth);
    }
    return published > 0;
  }

  public Optional<OcfSnapshot> findByFundAndMonth(String fundCode, LocalDate snapshotMonth) {
    return jdbcClient
        .sql(
            """
            SELECT * FROM investment_ocf_snapshot
            WHERE fund_code = :fundCode AND snapshot_month = :snapshotMonth
            ORDER BY version DESC
            LIMIT 1
            """)
        .param("fundCode", fundCode)
        .param("snapshotMonth", snapshotMonth)
        .query(OcfSnapshot::fromResultSet)
        .optional();
  }

  public Optional<OcfSnapshot> findPublishedByFundAndMonth(
      String fundCode, LocalDate snapshotMonth) {
    return jdbcClient
        .sql(
            """
            SELECT * FROM investment_ocf_snapshot
            WHERE fund_code = :fundCode AND snapshot_month = :snapshotMonth
              AND published_at IS NOT NULL
            ORDER BY published_at DESC, id DESC
            LIMIT 1
            """)
        .param("fundCode", fundCode)
        .param("snapshotMonth", snapshotMonth)
        .query(OcfSnapshot::fromResultSet)
        .optional();
  }

  public List<OcfSnapshot> findAllVersions(String fundCode, LocalDate snapshotMonth) {
    return jdbcClient
        .sql(
            """
            SELECT * FROM investment_ocf_snapshot
            WHERE fund_code = :fundCode AND snapshot_month = :snapshotMonth
            ORDER BY version
            """)
        .param("fundCode", fundCode)
        .param("snapshotMonth", snapshotMonth)
        .query(OcfSnapshot::fromResultSet)
        .list();
  }

  public Optional<OcfSnapshot> findLatestByFund(String fundCode) {
    return jdbcClient
        .sql(
            """
            SELECT * FROM investment_ocf_snapshot
            WHERE fund_code = :fundCode
            ORDER BY snapshot_month DESC, version DESC
            LIMIT 1
            """)
        .param("fundCode", fundCode)
        .query(OcfSnapshot::fromResultSet)
        .optional();
  }

  public List<OcfSnapshot> findByFund(String fundCode) {
    return jdbcClient
        .sql(
            """
            SELECT s.* FROM investment_ocf_snapshot s
            JOIN (SELECT snapshot_month, MAX(version) AS version
                    FROM investment_ocf_snapshot
                   WHERE fund_code = :fundCode
                   GROUP BY snapshot_month) latest
              ON latest.snapshot_month = s.snapshot_month AND latest.version = s.version
            WHERE s.fund_code = :fundCode
            ORDER BY s.snapshot_month DESC
            """)
        .param("fundCode", fundCode)
        .query(OcfSnapshot::fromResultSet)
        .list();
  }

  public BigDecimal findLatestTotalOcfByFund(String fundCode) {
    return jdbcClient
        .sql(
            """
            SELECT total_ocf FROM investment_ocf_snapshot
            WHERE fund_code = :fundCode
            ORDER BY snapshot_month DESC, version DESC
            LIMIT 1
            """)
        .param("fundCode", fundCode)
        .query(BigDecimal.class)
        .optional()
        .orElse(BigDecimal.ZERO);
  }
}
