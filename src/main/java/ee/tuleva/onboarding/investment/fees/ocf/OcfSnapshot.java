package ee.tuleva.onboarding.investment.fees.ocf;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

public record OcfSnapshot(
    Long id,
    String fundCode,
    LocalDate snapshotMonth,
    BigDecimal managementFeeRate,
    BigDecimal depotFeeRate,
    BigDecimal underlyingFundCost,
    BigDecimal transactionCostRate,
    BigDecimal totalOcf) {

  public static OcfSnapshot fromResultSet(ResultSet rs, int rowNum) throws SQLException {
    return new OcfSnapshot(
        rs.getLong("id"),
        rs.getString("fund_code"),
        rs.getDate("snapshot_month").toLocalDate(),
        rs.getBigDecimal("management_fee_rate"),
        rs.getBigDecimal("depot_fee_rate"),
        rs.getBigDecimal("underlying_fund_cost"),
        rs.getBigDecimal("transaction_cost_rate"),
        rs.getBigDecimal("total_ocf"));
  }
}
