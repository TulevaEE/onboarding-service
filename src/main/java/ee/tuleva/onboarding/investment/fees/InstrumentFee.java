package ee.tuleva.onboarding.investment.fees;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import lombok.Builder;

@Builder
public record InstrumentFee(
    Long id,
    String isin,
    String instrumentName,
    BigDecimal publishedOcf,
    BigDecimal rebateRate,
    BigDecimal netOcf,
    LocalDate validFrom,
    LocalDate validTo,
    String source) {

  public static InstrumentFee fromResultSet(ResultSet rs, int rowNum) throws SQLException {
    return new InstrumentFee(
        rs.getLong("id"),
        rs.getString("isin"),
        rs.getString("instrument_name"),
        rs.getBigDecimal("published_ocf"),
        rs.getBigDecimal("rebate_rate"),
        rs.getBigDecimal("net_ocf"),
        rs.getDate("valid_from").toLocalDate(),
        rs.getDate("valid_to") != null ? rs.getDate("valid_to").toLocalDate() : null,
        rs.getString("source"));
  }
}
