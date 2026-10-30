package ee.tuleva.onboarding.investment.fees.rate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
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

  List<InstrumentFeeAgreement> findAllInForceDuring(YearMonth month) {
    return jdbcClient
        .sql(
            """
            SELECT * FROM investment_instrument_fee
            WHERE valid_from <= :monthEnd
              AND (valid_to IS NULL OR valid_to >= :monthStart)
            ORDER BY isin, valid_from
            """)
        .param("monthStart", month.atDay(1))
        .param("monthEnd", month.atEndOfMonth())
        .query(InstrumentFeeAgreementRepository::agreement)
        .list();
  }

  List<AgreementPeriod> findAllStartedBy(LocalDate date) {
    return jdbcClient
        .sql(
            """
            SELECT id, isin, published_ocf, valid_from, valid_to FROM investment_instrument_fee
            WHERE valid_from <= :date
            ORDER BY isin, valid_from
            """)
        .param("date", date)
        .query(
            (rs, rowNum) ->
                new AgreementPeriod(
                    rs.getLong("id"),
                    rs.getString("isin"),
                    rs.getBigDecimal("published_ocf"),
                    rs.getObject("valid_from", LocalDate.class),
                    rs.getObject("valid_to", LocalDate.class)))
        .list();
  }

  Set<Long> findIdsAppliedDuring(YearMonth month) {
    return new AgreementsInForce(findAllStartedBy(month.atEndOfMonth())).appliedDuring(month);
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
