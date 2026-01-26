package ee.tuleva.onboarding.investment.fees;

import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CustodyFeeInstrumentTypeRepository {

  private final JdbcClient jdbcClient;

  public List<CustodyFeeInstrumentType> findAllValidOn(LocalDate date) {
    return jdbcClient
        .sql(
            """
            SELECT id, isin, instrument_type, annual_rate, valid_from, valid_to
            FROM custody_fee_instrument_type
            WHERE valid_from <= :date
              AND (valid_to IS NULL OR valid_to >= :date)
            ORDER BY isin
            """)
        .param("date", date)
        .query(CustodyFeeInstrumentType::fromResultSet)
        .list();
  }
}
