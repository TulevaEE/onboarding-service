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
            MERGE INTO investment_ocf_snapshot AS t
            USING (VALUES (:fundCode, :snapshotMonth, :managementFeeRate, :depotFeeRate,
                           :underlyingFundCost, :transactionCostRate, :totalOcf))
              AS s(fund_code, snapshot_month, management_fee_rate, depot_fee_rate,
                   underlying_fund_cost, transaction_cost_rate, total_ocf)
            ON t.fund_code = s.fund_code AND t.snapshot_month = s.snapshot_month
            WHEN MATCHED THEN UPDATE SET
              management_fee_rate = s.management_fee_rate,
              depot_fee_rate = s.depot_fee_rate,
              underlying_fund_cost = s.underlying_fund_cost,
              transaction_cost_rate = s.transaction_cost_rate,
              total_ocf = s.total_ocf
            WHEN NOT MATCHED THEN INSERT
              (fund_code, snapshot_month, management_fee_rate, depot_fee_rate,
               underlying_fund_cost, transaction_cost_rate, total_ocf)
            VALUES (s.fund_code, s.snapshot_month, s.management_fee_rate, s.depot_fee_rate,
                    s.underlying_fund_cost, s.transaction_cost_rate, s.total_ocf)
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
