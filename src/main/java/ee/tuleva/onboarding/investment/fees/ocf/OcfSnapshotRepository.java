package ee.tuleva.onboarding.investment.fees.ocf;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Repository
@RequiredArgsConstructor
public class OcfSnapshotRepository {

  private final JdbcClient jdbcClient;
  private final TransactionTemplate transactionTemplate;

  public void save(OcfSnapshot snapshot, List<OcfHolding> holdings) {
    try {
      transactionTemplate.executeWithoutResult(
          status -> saveWorkingVersionWithItsHoldings(snapshot, holdings));
    } catch (DuplicateKeyException versionNumberTakenByAConcurrentInsert) {
      transactionTemplate.executeWithoutResult(
          status ->
              writeIntoTheVersionThatWonTheRace(
                  snapshot, holdings, versionNumberTakenByAConcurrentInsert));
    }
  }

  private void saveWorkingVersionWithItsHoldings(OcfSnapshot snapshot, List<OcfHolding> holdings) {
    if (updateWorkingVersion(snapshot) == 0) {
      insertNextVersion(snapshot);
    }
    replaceHoldingsOfTheWorkingVersion(snapshot, holdings);
  }

  private void replaceHoldingsOfTheWorkingVersion(OcfSnapshot snapshot, List<OcfHolding> holdings) {
    var snapshotId =
        jdbcClient
            .sql(
                """
                SELECT id FROM investment_ocf_snapshot
                WHERE fund_code = :fundCode AND snapshot_month = :snapshotMonth
                  AND published_at IS NULL
                """)
            .param("fundCode", snapshot.fundCode())
            .param("snapshotMonth", snapshot.snapshotMonth())
            .query(Long.class)
            .single();
    jdbcClient
        .sql("DELETE FROM investment_ocf_snapshot_detail WHERE snapshot_id = :snapshotId")
        .param("snapshotId", snapshotId)
        .update();
    holdings.forEach(
        holding ->
            jdbcClient
                .sql(
                    """
                    INSERT INTO investment_ocf_snapshot_detail
                      (snapshot_id, isin, weight, published_ocf, net_ocf, rate_basis,
                       instrument_fee_rate_id, instrument_fee_id)
                    VALUES
                      (:snapshotId, :isin, :weight, :publishedOcf, :netOcf, :rateBasis,
                       :instrumentFeeRateId, :instrumentFeeId)
                    """)
                .param("snapshotId", snapshotId)
                .param("isin", holding.rate().isin())
                .param("weight", holding.weight())
                .param("publishedOcf", holding.rate().publishedOcf())
                .param("netOcf", holding.rate().netOcf())
                .param("rateBasis", holding.rate().basis().name())
                .param("instrumentFeeRateId", holding.rate().rateId())
                .param("instrumentFeeId", holding.rate().agreementId())
                .update());
  }

  private void writeIntoTheVersionThatWonTheRace(
      OcfSnapshot snapshot,
      List<OcfHolding> holdings,
      DuplicateKeyException versionNumberTakenByAConcurrentInsert) {
    if (updateWorkingVersion(snapshot) == 0) {
      throw versionNumberTakenByAConcurrentInsert;
    }
    replaceHoldingsOfTheWorkingVersion(snapshot, holdings);
    log.info(
        "Concurrent OCF snapshot insert for fund={}, month={}; wrote into the winning version",
        snapshot.fundCode(),
        snapshot.snapshotMonth());
  }

  private int updateWorkingVersion(OcfSnapshot snapshot) {
    return OcfSnapshotParameters.bind(
            jdbcClient.sql(
                """
                UPDATE investment_ocf_snapshot SET
                  management_fee_rate = :managementFeeRate,
                  depot_fee_rate = :depotFeeRate,
                  underlying_fund_cost = :underlyingFundCost,
                  underlying_fund_cost_gross = :underlyingFundCostGross,
                  underlying_fund_cost_net = :underlyingFundCostNet,
                  rebate_basis = :rebateBasis,
                  methodology = :methodology,
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
                  txn_fees = :txnFees,
                  txn_average_aum = :txnAverageAum,
                  txn_nav_dates = :txnNavDates,
                  underlying_weight_basis = :underlyingWeightBasis,
                  invested_ratio_month_end = :investedRatioMonthEnd,
                  invested_ratio_month_average = :investedRatioMonthAverage,
                  calculated_at = now()
                WHERE fund_code = :fundCode
                  AND snapshot_month = :snapshotMonth
                  AND published_at IS NULL
                """),
            snapshot)
        .update();
  }

  private void insertNextVersion(OcfSnapshot snapshot) {
    OcfSnapshotParameters.bind(
            jdbcClient.sql(
                """
                INSERT INTO investment_ocf_snapshot
                  (fund_code, snapshot_month, version, management_fee_rate, depot_fee_rate,
                   underlying_fund_cost, underlying_fund_cost_gross, underlying_fund_cost_net,
                   rebate_basis, methodology,
                   transaction_cost_rate, total_ocf, complete, checks,
                   nav_date, nav_calculation_id, assets_under_management, management_fee_rate_id,
                   depot_charged_to_fund, depot_tier_nav_date, depot_tier_basis,
                   txn_window_start, txn_window_end, txn_fees, txn_average_aum,
                   txn_nav_dates, underlying_weight_basis, invested_ratio_month_end,
                   invested_ratio_month_average)
                SELECT :fundCode, :snapshotMonth, COALESCE(MAX(version), 0) + 1,
                       :managementFeeRate, :depotFeeRate, :underlyingFundCost,
                       :underlyingFundCostGross, :underlyingFundCostNet, :rebateBasis,
                       :methodology,
                       :transactionCostRate, :totalOcf, :complete, :checks,
                       :navDate, :navCalculationId, :assetsUnderManagement, :managementFeeRateId,
                       :depotChargedToFund, :depotTierNavDate, :depotTierBasis,
                       :txnWindowStart, :txnWindowEnd, :txnFees, :txnAverageAum,
                       :txnNavDates, :underlyingWeightBasis, :investedRatioMonthEnd,
                       :investedRatioMonthAverage
                FROM investment_ocf_snapshot
                WHERE fund_code = :fundCode AND snapshot_month = :snapshotMonth
                """),
            snapshot)
        .update();
  }

  Optional<OcfSnapshot> findVersion(String fundCode, LocalDate snapshotMonth, int version) {
    return jdbcClient
        .sql(
            """
            SELECT * FROM investment_ocf_snapshot
            WHERE fund_code = :fundCode AND snapshot_month = :snapshotMonth AND version = :version
            """)
        .param("fundCode", fundCode)
        .param("snapshotMonth", snapshotMonth)
        .param("version", version)
        .query(OcfSnapshot::fromResultSet)
        .optional();
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

  boolean anyVersionPublished(String fundCode, LocalDate snapshotMonth) {
    return jdbcClient
        .sql(
            """
            SELECT 1 FROM investment_ocf_snapshot
            WHERE fund_code = :fundCode AND snapshot_month = :snapshotMonth
              AND published_at IS NOT NULL
            LIMIT 1
            """)
        .param("fundCode", fundCode)
        .param("snapshotMonth", snapshotMonth)
        .query(Integer.class)
        .optional()
        .isPresent();
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
