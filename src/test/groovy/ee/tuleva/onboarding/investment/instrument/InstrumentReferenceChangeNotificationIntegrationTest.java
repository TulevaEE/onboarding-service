package ee.tuleva.onboarding.investment.instrument;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import ee.tuleva.onboarding.instrument.InstrumentReferenceHistoryRepository;
import ee.tuleva.onboarding.notification.email.EmailService;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class InstrumentReferenceChangeNotificationIntegrationTest {

  private static final String ISIN = "IE00B4L5Y983";
  private static final String ORIGINAL_NAME = "iShares Core MSCI World UCITS ETF";
  private static final String RENAMED = "iShares Core MSCI World UCITS ETF (renamed)";

  @Autowired private InstrumentValidationJob job;
  @Autowired private InstrumentReferenceHistoryRepository historyRepository;
  @Autowired private JdbcClient jdbcClient;
  @Autowired private DataSource dataSource;

  @MockitoBean private EmailService emailService;

  @AfterEach
  void restoreInstrument() throws SQLException {
    if (!isPostgres()) {
      return;
    }
    jdbcClient
        .sql("UPDATE instrument_reference SET display_name = :name WHERE isin = :isin")
        .param("name", ORIGINAL_NAME)
        .param("isin", ISIN)
        .update();
    jdbcClient
        .sql(
            "UPDATE instrument_reference_history SET notified_at = now() WHERE notified_at IS NULL")
        .update();
  }

  @Test
  void changingAnInstrumentLeavesAnAttributedHistoryRowAndProducesExactlyOneMail()
      throws SQLException {
    assumeTrue(isPostgres(), "The history trigger is plpgsql and only exists on PostgreSQL");
    given(emailService.sendSystemEmail(any())).willReturn(true);

    jdbcClient
        .sql("UPDATE instrument_reference SET display_name = :name WHERE isin = :isin")
        .param("name", RENAMED)
        .param("isin", ISIN)
        .update();

    var changes = historyRepository.unnotifiedChanges();

    assertThat(changes)
        .singleElement()
        .satisfies(
            change -> {
              assertThat(change.isin()).isEqualTo(ISIN);
              assertThat(change.operation()).isEqualTo("UPDATE");
              assertThat(change.changedBy()).isNotBlank();
              assertThat(change.changedAt()).isNotNull();
              assertThat(change.oldValues()).contains(ORIGINAL_NAME);
              assertThat(change.newValues()).contains(RENAMED);
            });

    job.run();

    verify(emailService, times(1)).sendSystemEmail(argThat(this::isChangeNotification));
    assertThat(historyRepository.unnotifiedChanges()).isEmpty();
  }

  private boolean isChangeNotification(MandrillMessage message) {
    return "[CHANGED] Instrument reference data".equals(message.getSubject())
        && "funds@tuleva.ee".equals(message.getTo().getFirst().getEmail())
        && message.getText().contains("display_name: %s -> %s".formatted(ORIGINAL_NAME, RENAMED));
  }

  private boolean isPostgres() throws SQLException {
    try (var connection = dataSource.getConnection()) {
      return connection.getMetaData().getDatabaseProductName().toLowerCase().contains("postgresql");
    }
  }
}
