package ee.tuleva.onboarding.investment.fees.ocf;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public record OcfAudit(
    @Nullable LocalDate navDate,
    @Nullable UUID navCalculationId,
    @Nullable BigDecimal assetsUnderManagement,
    @Nullable Long managementFeeRateId,
    @Nullable Boolean depotChargedToFund,
    @Nullable LocalDate depotTierNavDate,
    @Nullable BigDecimal depotTierBasis,
    @Nullable LocalDate txnWindowStart,
    @Nullable LocalDate txnWindowEnd,
    @Nullable BigDecimal txnCommissions,
    @Nullable BigDecimal txnAverageAum,
    @Nullable String txnNavDates) {

  public static OcfAudit empty() {
    return new OcfAudit(null, null, null, null, null, null, null, null, null, null, null, null);
  }

  public static OcfAudit fromResultSet(ResultSet rs) throws SQLException {
    return new OcfAudit(
        localDate(rs, "nav_date"),
        uuid(rs, "nav_calculation_id"),
        rs.getBigDecimal("assets_under_management"),
        longOrNull(rs, "management_fee_rate_id"),
        booleanOrNull(rs, "depot_charged_to_fund"),
        localDate(rs, "depot_tier_nav_date"),
        rs.getBigDecimal("depot_tier_basis"),
        localDate(rs, "txn_window_start"),
        localDate(rs, "txn_window_end"),
        rs.getBigDecimal("txn_commissions"),
        rs.getBigDecimal("txn_average_aum"),
        rs.getString("txn_nav_dates"));
  }

  private static @Nullable LocalDate localDate(ResultSet rs, String column) throws SQLException {
    var value = rs.getDate(column);
    return value == null ? null : value.toLocalDate();
  }

  private static @Nullable UUID uuid(ResultSet rs, String column) throws SQLException {
    var value = rs.getString(column);
    return value == null ? null : UUID.fromString(value);
  }

  private static @Nullable Long longOrNull(ResultSet rs, String column) throws SQLException {
    var value = rs.getLong(column);
    return rs.wasNull() ? null : value;
  }

  private static @Nullable Boolean booleanOrNull(ResultSet rs, String column) throws SQLException {
    var value = rs.getBoolean(column);
    return rs.wasNull() ? null : value;
  }
}
