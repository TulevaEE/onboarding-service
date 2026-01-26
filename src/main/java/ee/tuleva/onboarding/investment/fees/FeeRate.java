package ee.tuleva.onboarding.investment.fees;

import ee.tuleva.onboarding.investment.TulevaFund;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

public record FeeRate(
    Long id,
    TulevaFund fund,
    FeeType feeType,
    BigDecimal annualRate,
    LocalDate validFrom,
    LocalDate validTo) {

  public static FeeRate fromResultSet(ResultSet rs, int rowNum) throws SQLException {
    return new FeeRate(
        rs.getLong("id"),
        TulevaFund.fromCode(rs.getString("fund_code")),
        FeeType.valueOf(rs.getString("fee_type")),
        rs.getBigDecimal("annual_rate"),
        rs.getDate("valid_from").toLocalDate(),
        rs.getDate("valid_to") != null ? rs.getDate("valid_to").toLocalDate() : null);
  }
}
