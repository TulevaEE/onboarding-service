package ee.tuleva.onboarding.investment.fees.ocf;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class OcfSnapshotRepository {

  private final JdbcClient jdbcClient;

  public void save(OcfSnapshot snapshot) {
    jdbcClient
        .sql(
            """
            INSERT INTO investment_ocf_snapshot
              (fund_code, snapshot_month, management_fee_rate, depot_fee_rate,
               underlying_fund_cost, transaction_cost_rate, total_ocf)
            VALUES
              (:fundCode, :snapshotMonth, :managementFeeRate, :depotFeeRate,
               :underlyingFundCost, :transactionCostRate, :totalOcf)
            ON CONFLICT (fund_code, snapshot_month) DO UPDATE SET
              management_fee_rate = EXCLUDED.management_fee_rate,
              depot_fee_rate = EXCLUDED.depot_fee_rate,
              underlying_fund_cost = EXCLUDED.underlying_fund_cost,
              transaction_cost_rate = EXCLUDED.transaction_cost_rate,
              total_ocf = EXCLUDED.total_ocf,
              created_at = now()
            """)
        .param("fundCode", snapshot.fundCode())
        .param("snapshotMonth", snapshot.snapshotMonth())
        .param("managementFeeRate", snapshot.managementFeeRate())
        .param("depotFeeRate", snapshot.depotFeeRate())
        .param("underlyingFundCost", snapshot.underlyingFundCost())
        .param("transactionCostRate", snapshot.transactionCostRate())
        .param("totalOcf", snapshot.totalOcf())
        .update();
  }

  public Optional<OcfSnapshot> findByFundAndMonth(String fundCode, LocalDate snapshotMonth) {
    return jdbcClient
        .sql(
            """
            SELECT * FROM investment_ocf_snapshot
            WHERE fund_code = :fundCode AND snapshot_month = :snapshotMonth
            """)
        .param("fundCode", fundCode)
        .param("snapshotMonth", snapshotMonth)
        .query(OcfSnapshot::fromResultSet)
        .optional();
  }

  public Optional<OcfSnapshot> findLatestByFund(String fundCode) {
    return jdbcClient
        .sql(
            """
            SELECT * FROM investment_ocf_snapshot
            WHERE fund_code = :fundCode
            ORDER BY snapshot_month DESC
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
            SELECT * FROM investment_ocf_snapshot
            WHERE fund_code = :fundCode
            ORDER BY snapshot_month DESC
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
            ORDER BY snapshot_month DESC
            LIMIT 1
            """)
        .param("fundCode", fundCode)
        .query(BigDecimal.class)
        .optional()
        .orElse(BigDecimal.ZERO);
  }
}
