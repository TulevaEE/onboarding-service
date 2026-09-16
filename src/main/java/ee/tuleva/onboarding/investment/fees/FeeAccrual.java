package ee.tuleva.onboarding.investment.fees;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Objects;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

@Builder(toBuilder = true)
public record FeeAccrual(
    Long id,
    TulevaFund fund,
    FeeType feeType,
    LocalDate accrualDate,
    LocalDate feeMonth,
    BigDecimal baseValue,
    BigDecimal annualRate,
    BigDecimal dailyAmountGross,
    int daysInYear,
    @Nullable LocalDate referenceDate) {

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
        rs.getBigDecimal("daily_amount_gross"),
        rs.getInt("days_in_year"),
        referenceDateSql != null ? referenceDateSql.toLocalDate() : null);
  }

  public boolean sameValuesAs(FeeAccrual other) {
    return fund == other.fund
        && feeType == other.feeType
        && accrualDate.equals(other.accrualDate)
        && feeMonth.equals(other.feeMonth)
        && baseValue.compareTo(other.baseValue) == 0
        && annualRate.compareTo(other.annualRate) == 0
        && dailyAmountGross.compareTo(other.dailyAmountGross) == 0
        && daysInYear == other.daysInYear
        && Objects.equals(referenceDate, other.referenceDate);
  }
}
