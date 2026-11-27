package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.SELL;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ee.tuleva.onboarding.investment.transaction.TransactionType;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

@DataJpaTest
class SebFundConfirmationRepositoryIT {

  private static final Instant NOW = Instant.parse("2026-01-08T10:00:00Z");

  @Autowired private JdbcClient jdbcClient;

  private SebFundConfirmationRepository repository;

  @BeforeEach
  void setUp() {
    repository = new SebFundConfirmationRepository(jdbcClient);
  }

  @Test
  void findByTradeReference_returnsASubscriptionAndARedemptionAsTheyWereRecorded() {
    RecordedSebFundConfirmation subscription = confirmation("DLA0000001", BUY);
    RecordedSebFundConfirmation redemption = confirmation("DLA0000002", SELL);

    repository.insert(subscription, NOW);
    repository.insert(redemption, NOW);

    assertThat(repository.findByTradeReference("DLA0000001")).contains(subscription);
    assertThat(repository.findByTradeReference("DLA0000002")).contains(redemption);
  }

  @Test
  @EnabledIfEnvironmentVariable(named = "SPRING_PROFILES_ACTIVE", matches = ".*(pg|ci).*")
  void theTableTakesOnlyABuyOrASell() {
    assertThatThrownBy(
            () ->
                jdbcClient
                    .sql(
                        """
                        INSERT INTO investment_seb_fund_confirmation (
                          trade_reference, fund_code, instrument_isin, transaction_type,
                          order_received_at, pricing_date, source_file_key,
                          created_at, updated_at)
                        VALUES (
                          'DLA0000003', 'TUK75', 'IE00BKPTWY98', 'SWITCH',
                          :now, '2026-01-06', 'seb-confirmations/DLA0000003-001.pdf',
                          :now, :now)
                        """)
                    .param("now", Timestamp.from(NOW))
                    .update())
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private static RecordedSebFundConfirmation confirmation(String reference, TransactionType side) {
    return new RecordedSebFundConfirmation(
        reference,
        TUK75,
        "IE00BKPTWY98",
        side,
        Instant.parse("2026-01-05T09:48:17Z"),
        LocalDate.of(2026, 1, 6),
        "seb-confirmations/" + reference + "-001.pdf");
  }
}
