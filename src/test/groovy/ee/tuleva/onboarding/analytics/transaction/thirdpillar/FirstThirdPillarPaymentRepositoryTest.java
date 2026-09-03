package ee.tuleva.onboarding.analytics.transaction.thirdpillar;

import static ee.tuleva.onboarding.analytics.AnalyticsThirdPillarTransactionFixture.CREATE_ANALYTICS_SCHEMA;
import static ee.tuleva.onboarding.analytics.AnalyticsThirdPillarTransactionFixture.CREATE_THIRD_PILLAR_TABLE;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDate;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@JdbcTest
@Import(FirstThirdPillarPaymentRepository.class)
class FirstThirdPillarPaymentRepositoryTest {

  @Autowired private FirstThirdPillarPaymentRepository repository;
  @Autowired private DataSource dataSource;
  @Autowired private JdbcClient jdbcClient;

  @BeforeEach
  void setUp() throws Exception {
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute(CREATE_ANALYTICS_SCHEMA);
      statement.execute(CREATE_THIRD_PILLAR_TABLE);
      statement.execute("TRUNCATE TABLE analytics.third_pillar_transactions");
    }
  }

  private void insertTransaction(String source, LocalDate reportingDate) {
    jdbcClient
        .sql(
            """
            INSERT INTO analytics.third_pillar_transactions
              (reporting_date, full_name, personal_id, account_no, transaction_type,
               transaction_source, share_amount, share_price, nav, transaction_value)
            VALUES (:reportingDate, 'John Doe', '38888888888', 'EE123', 'Contribution',
                    :source, 10, 10, 100, 100)
            """)
        .param("reportingDate", reportingDate)
        .param("source", source)
        .update();
  }

  @Test
  void oldestOwnPaymentDateIsEmptyWhenNoTransactionsExist() {
    assertThat(repository.oldestOwnPaymentDate()).isEmpty();
  }

  @Test
  void oldestOwnPaymentDateReturnsTheEarliestOwnMoneyContribution() {
    insertTransaction(FirstThirdPillarPaymentRepository.OWN_MONEY_SOURCE, LocalDate.of(2024, 6, 1));
    insertTransaction(
        FirstThirdPillarPaymentRepository.OWN_MONEY_SOURCE, LocalDate.of(2023, 3, 15));
    insertTransaction("Osakute väljalase tööandjalt laekumiste alusel", LocalDate.of(2020, 1, 1));

    assertThat(repository.oldestOwnPaymentDate()).contains(LocalDate.of(2023, 3, 15));
  }

  @Test
  void oldestOwnPaymentDateIgnoresContributionsFromOtherSources() {
    insertTransaction("Osakute väljalase tööandjalt laekumiste alusel", LocalDate.of(2019, 1, 1));

    assertThat(repository.oldestOwnPaymentDate()).isEmpty();
  }
}
