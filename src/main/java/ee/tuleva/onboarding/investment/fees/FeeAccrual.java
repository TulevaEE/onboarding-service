package ee.tuleva.onboarding.investment.fees;

import ee.tuleva.onboarding.investment.TulevaFund;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import lombok.Builder;

@Builder
public record FeeAccrual(
    Long id,
    TulevaFund fund,
    FeeType feeType,
    LocalDate accrualDate,
    LocalDate feeMonth,
    BigDecimal baseValue,
    BigDecimal annualRate,
    BigDecimal dailyAmountNet,
    BigDecimal dailyAmountGross,
    BigDecimal vatRate,
    int daysInYear,
    LocalDate referenceDate) {

  public static FeeAccrual fromResultSet(ResultSet rs, int rowNum) throws SQLException {
    Date referenceDateSql = rs.getDate("reference_date");
    return new FeeAccrual(
        rs.getLong("id"),
        TulevaFund.fromCode(rs.getString("fund_code")),
        FeeType.valueOf(rs.getString("fee_type")),
        rs.getDate("accrual_date").toLocalDate(),
        rs.getDate("fee_month").toLocalDate(),
        rs.getBigDecimal("base_value"),
        rs.getBigDecimal("annual_rate"),
        rs.getBigDecimal("daily_amount_net"),
        rs.getBigDecimal("daily_amount_gross"),
        rs.getBigDecimal("vat_rate"),
        rs.getInt("days_in_year"),
        referenceDateSql != null ? referenceDateSql.toLocalDate() : null);
  }
}
