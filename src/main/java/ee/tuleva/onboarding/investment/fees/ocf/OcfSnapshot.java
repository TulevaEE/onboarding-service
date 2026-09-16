package ee.tuleva.onboarding.investment.fees.ocf;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public record OcfSnapshot(
    @Nullable Long id,
    String fundCode,
    LocalDate snapshotMonth,
    int version,
    BigDecimal managementFeeRate,
    BigDecimal depotFeeRate,
    BigDecimal underlyingFundCost,
    BigDecimal transactionCostRate,
    BigDecimal totalOcf,
    boolean complete,
    @Nullable String checks,
    @Nullable Instant publishedAt,
    @Nullable String publishedIn,
    OcfAudit audit) {

  private static final int UNVERSIONED = 0;

  public static OcfSnapshot computed(
      String fundCode,
      LocalDate snapshotMonth,
      BigDecimal managementFeeRate,
      BigDecimal depotFeeRate,
      BigDecimal underlyingFundCost,
      BigDecimal transactionCostRate,
      BigDecimal totalOcf,
      boolean complete,
      @Nullable String checks,
      OcfAudit audit) {
    return new OcfSnapshot(
        null,
        fundCode,
        snapshotMonth,
        UNVERSIONED,
        managementFeeRate,
        depotFeeRate,
        underlyingFundCost,
        transactionCostRate,
        totalOcf,
        complete,
        checks,
        null,
        null,
        audit);
  }

  public static OcfSnapshot fromResultSet(ResultSet rs, int rowNum) throws SQLException {
    var publishedAt = rs.getTimestamp("published_at");
    return new OcfSnapshot(
        rs.getLong("id"),
        rs.getString("fund_code"),
        rs.getDate("snapshot_month").toLocalDate(),
        rs.getInt("version"),
        rs.getBigDecimal("management_fee_rate"),
        rs.getBigDecimal("depot_fee_rate"),
        rs.getBigDecimal("underlying_fund_cost"),
        rs.getBigDecimal("transaction_cost_rate"),
        rs.getBigDecimal("total_ocf"),
        rs.getBoolean("complete"),
        rs.getString("checks"),
        publishedAt == null ? null : publishedAt.toInstant(),
        rs.getString("published_in"),
        OcfAudit.fromResultSet(rs));
  }
}
