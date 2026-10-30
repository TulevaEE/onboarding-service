package ee.tuleva.onboarding.investment.fees.ocf;

import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@DataJpaTest
@Import(HistoricalTransactionFeeRepository.class)
class HistoricalTransactionFeeRepositoryTest {

  private static final LocalDate FROM = LocalDate.of(2025, 10, 1);
  private static final LocalDate TO = LocalDate.of(2026, 9, 30);

  @Autowired private JdbcClient jdbcClient;
  @Autowired private HistoricalTransactionFeeRepository repository;

  @BeforeEach
  void setUp() {
    jdbcClient.sql("DELETE FROM investment_historical_transaction_fee").update();
  }

  private void fee(String fundCode, LocalDate valueDate, String amount) {
    jdbcClient
        .sql(
            """
            INSERT INTO investment_historical_transaction_fee (fund_code, value_date, amount, source)
            VALUES (:fundCode, :valueDate, CAST(:amount AS numeric), 'test-upload')
            """)
        .param("fundCode", fundCode)
        .param("valueDate", valueDate)
        .param("amount", amount)
        .update();
  }

  @Test
  void sumsTheFundsFeesWithBothEndsOfTheWindowIncluded() {
    fee("TUK75", FROM, "1.25");
    fee("TUK75", LocalDate.of(2026, 1, 15), "3.00");
    fee("TUK75", TO, "0.75");
    fee("TUK75", FROM.minusDays(1), "99.00");
    fee("TUK75", TO.plusDays(1), "99.00");
    fee("TUV100", LocalDate.of(2026, 1, 15), "77.00");

    assertThat(repository.sumForFundBetween(TUK75, FROM, TO)).isEqualByComparingTo("5.00");
  }

  @Test
  void aFundWithNoFeesInTheWindowSumsToZero() {
    fee("TUK75", LocalDate.of(2026, 1, 15), "3.00");

    assertThat(repository.sumForFundBetween(TUV100, FROM, TO)).isEqualByComparingTo("0");
  }
}
