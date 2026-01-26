package ee.tuleva.onboarding.investment.fees;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

public record CustodyFeeInstrumentType(
    Long id,
    String isin,
    CustodyInstrumentType instrumentType,
    BigDecimal annualRate,
    LocalDate validFrom,
    LocalDate validTo) {

  public static CustodyFeeInstrumentType fromResultSet(ResultSet rs, int rowNum)
      throws SQLException {
    return new CustodyFeeInstrumentType(
        rs.getLong("id"),
        rs.getString("isin"),
        CustodyInstrumentType.valueOf(rs.getString("instrument_type")),
        rs.getBigDecimal("annual_rate"),
        rs.getDate("valid_from").toLocalDate(),
        rs.getDate("valid_to") != null ? rs.getDate("valid_to").toLocalDate() : null);
  }
}
