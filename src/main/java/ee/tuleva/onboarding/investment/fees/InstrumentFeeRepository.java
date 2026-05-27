package ee.tuleva.onboarding.investment.fees;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class InstrumentFeeRepository {

  private final JdbcClient jdbcClient;

  public Optional<InstrumentFee> findValidRate(String isin, LocalDate date) {
    return jdbcClient
        .sql(
            """
            SELECT * FROM investment_instrument_fee
            WHERE isin = :isin
              AND valid_from <= :date
              AND (valid_to IS NULL OR valid_to >= :date)
            ORDER BY valid_from DESC
            LIMIT 1
            """)
        .param("isin", isin)
        .param("date", date)
        .query(InstrumentFee::fromResultSet)
        .optional();
  }

  public List<InstrumentFee> findAllValidRates(LocalDate date) {
    return jdbcClient
        .sql(
            """
            SELECT DISTINCT ON (isin) * FROM investment_instrument_fee
            WHERE valid_from <= :date
              AND (valid_to IS NULL OR valid_to >= :date)
            ORDER BY isin, valid_from DESC
            """)
        .param("date", date)
        .query(InstrumentFee::fromResultSet)
        .list();
  }
}
