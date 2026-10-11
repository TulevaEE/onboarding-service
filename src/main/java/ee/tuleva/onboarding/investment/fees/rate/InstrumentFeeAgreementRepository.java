package ee.tuleva.onboarding.investment.fees.rate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class InstrumentFeeAgreementRepository {

  static final String THE_AGREEMENT_OF_EACH_ISIN_VALID_ON_THE_DATE =
      """
      SELECT * FROM (
        SELECT *, ROW_NUMBER() OVER (PARTITION BY isin ORDER BY valid_from DESC) AS rn
        FROM investment_instrument_fee
        WHERE valid_from <= :date
          AND (valid_to IS NULL OR valid_to >= :date)
      ) ranked
      WHERE rn = 1
      """;

  private final JdbcClient jdbcClient;

  List<InstrumentFeeAgreement> findAllValidOn(LocalDate date) {
    return jdbcClient
        .sql(THE_AGREEMENT_OF_EACH_ISIN_VALID_ON_THE_DATE + "ORDER BY isin")
        .param("date", date)
        .query(InstrumentFeeAgreementRepository::agreement)
        .list();
  }

  private static InstrumentFeeAgreement agreement(ResultSet rs, int rowNum) throws SQLException {
    return new InstrumentFeeAgreement(
        rs.getLong("id"),
        rs.getString("isin"),
        rs.getBigDecimal("published_ocf"),
        RebateKind.valueOf(rs.getString("rebate_kind")),
        rs.getString("rebate_terms"),
        rs.getBigDecimal("invoiced_fee_rate"));
  }
}
