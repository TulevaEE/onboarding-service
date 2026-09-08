package ee.tuleva.onboarding.investment.instrument;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import ee.tuleva.onboarding.instrument.ReferenceDataHistoryRepository;
import ee.tuleva.onboarding.notification.email.EmailService;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ReferenceDataChangeNotificationIntegrationTest {

  private static final String CHANGE_SUBJECT = "[CHANGED] Instrument reference data";
  private static final String FUNDS_EMAIL = "funds@tuleva.ee";

  private static final String ISIN = "IE00B4L5Y983";
  private static final String ORIGINAL_NAME = "iShares Core MSCI World UCITS ETF";
  private static final String RENAMED = "iShares Core MSCI World UCITS ETF (renamed)";

  private static final String BOND_GLOBAL = "BOND_GLOBAL";
  private static final String CURRENT_PROXY_ISIN = "IE00BDBRDM35";
  private static final String NEW_PROXY_ISIN = "LU1708330318";
  private static final String NOT_A_PROXY_ISIN = "IE00BFNM3G45";

  @Autowired private InstrumentValidationJob job;
  @Autowired private ReferenceDataHistoryRepository historyRepository;
  @Autowired private JdbcClient jdbcClient;
  @Autowired private DataSource dataSource;

  @MockitoBean private EmailService emailService;

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
              assertThat(change.tableName()).isEqualTo("instrument_reference");
              assertThat(change.recordKey()).isEqualTo(ISIN);
              assertThat(change.operation()).isEqualTo("UPDATE");
              assertThat(change.changedBy()).isNotBlank();
              assertThat(change.changedAt()).isNotNull();
              assertThat(change.oldValues()).contains(ORIGINAL_NAME);
              assertThat(change.newValues()).contains(RENAMED);
            });

    job.run();

    verify(emailService, times(1))
        .sendSystemEmail(
            argThat(
                message ->
                    isChangeNotification(message)
                        && message
                            .getText()
                            .contains("display_name: %s -> %s".formatted(ORIGINAL_NAME, RENAMED))));
    assertThat(historyRepository.unnotifiedChanges()).isEmpty();
  }

  @Test
  void rePointingABenchmarkProxyLeavesAnAttributedHistoryRowAndProducesExactlyOneMail()
      throws SQLException {
    assumeTrue(isPostgres(), "The history trigger is plpgsql and only exists on PostgreSQL");
    given(emailService.sendSystemEmail(any())).willReturn(true);

    jdbcClient
        .sql(
            "UPDATE benchmark_category_proxy SET etf_proxy_isin = :isin, index_proxy_isin = :isin"
                + " WHERE benchmark_category = :category")
        .param("isin", NEW_PROXY_ISIN)
        .param("category", BOND_GLOBAL)
        .update();

    var changes = historyRepository.unnotifiedChanges();

    assertThat(changes)
        .singleElement()
        .satisfies(
            change -> {
              assertThat(change.tableName()).isEqualTo("benchmark_category_proxy");
              assertThat(change.recordKey()).isEqualTo(BOND_GLOBAL);
              assertThat(change.operation()).isEqualTo("UPDATE");
              assertThat(change.changedBy()).isNotBlank();
              assertThat(change.changedAt()).isNotNull();
              assertThat(change.oldValues()).contains(CURRENT_PROXY_ISIN);
              assertThat(change.newValues()).contains(NEW_PROXY_ISIN);
            });

    job.run();

    verify(emailService, times(1))
        .sendSystemEmail(
            argThat(
                message ->
                    isChangeNotification(message)
                        && message
                            .getText()
                            .contains(
                                "UPDATE benchmark_category_proxy %s by".formatted(BOND_GLOBAL))
                        && message
                            .getText()
                            .contains(
                                "etf_proxy_isin: %s -> %s"
                                    .formatted(CURRENT_PROXY_ISIN, NEW_PROXY_ISIN))));
    assertThat(historyRepository.unnotifiedChanges()).isEmpty();
  }

  private boolean isChangeNotification(MandrillMessage message) {
    return CHANGE_SUBJECT.equals(message.getSubject())
        && FUNDS_EMAIL.equals(message.getTo().getFirst().getEmail());
  }

  @Test
  void deactivatingAnInstrumentThatIsStillABenchmarkProxyIsRejected() throws SQLException {
    assumeTrue(isPostgres(), "The write guards are plpgsql and only exist on PostgreSQL");

    assertThat(isActive(CURRENT_PROXY_ISIN)).isTrue();

    assertThatThrownBy(() -> deactivate(CURRENT_PROXY_ISIN))
        .isInstanceOf(DataAccessException.class);
  }

  @Test
  void deactivatingAnInstrumentThatIsNotABenchmarkProxyIsAllowed() throws SQLException {
    assumeTrue(isPostgres(), "The write guards are plpgsql and only exist on PostgreSQL");

    deactivate(NOT_A_PROXY_ISIN);

    assertThat(isActive(NOT_A_PROXY_ISIN)).isFalse();
  }

  @Test
  void aProxyMayBeDeactivatedOnceItsCategoryPointsSomewhereElse() throws SQLException {
    assumeTrue(isPostgres(), "The write guards are plpgsql and only exist on PostgreSQL");

    rePointBondGlobalTo(NEW_PROXY_ISIN);
    deactivate(CURRENT_PROXY_ISIN);

    assertThat(isActive(CURRENT_PROXY_ISIN)).isFalse();
  }

  @Test
  void pointingABenchmarkCategoryAtAnInactiveInstrumentIsRejected() throws SQLException {
    assumeTrue(isPostgres(), "The write guards are plpgsql and only exist on PostgreSQL");

    deactivate(NEW_PROXY_ISIN);

    assertThatThrownBy(() -> rePointBondGlobalTo(NEW_PROXY_ISIN))
        .isInstanceOf(DataAccessException.class);
  }

  @Test
  void anUpdateStampsUpdatedAt() throws SQLException {
    assumeTrue(isPostgres(), "The write guards are plpgsql and only exist on PostgreSQL");

    var before = updatedAt(NOT_A_PROXY_ISIN);

    jdbcClient
        .sql("UPDATE instrument_reference SET display_name = :name WHERE isin = :isin")
        .param("name", "Renamed for the test")
        .param("isin", NOT_A_PROXY_ISIN)
        .update();

    assertThat(updatedAt(NOT_A_PROXY_ISIN)).isAfter(before);
  }

  private void deactivate(String isin) {
    jdbcClient
        .sql("UPDATE instrument_reference SET active = false WHERE isin = :isin")
        .param("isin", isin)
        .update();
  }

  private void rePointBondGlobalTo(String isin) {
    jdbcClient
        .sql(
            "UPDATE benchmark_category_proxy SET etf_proxy_isin = :isin, index_proxy_isin = :isin"
                + " WHERE benchmark_category = :category")
        .param("isin", isin)
        .param("category", BOND_GLOBAL)
        .update();
  }

  private boolean isActive(String isin) {
    return jdbcClient
        .sql("SELECT active FROM instrument_reference WHERE isin = :isin")
        .param("isin", isin)
        .query(Boolean.class)
        .single();
  }

  private Instant updatedAt(String isin) {
    return jdbcClient
        .sql("SELECT updated_at FROM instrument_reference WHERE isin = :isin")
        .param("isin", isin)
        .query(Timestamp.class)
        .single()
        .toInstant();
  }

  private boolean isPostgres() throws SQLException {
    try (var connection = dataSource.getConnection()) {
      return connection.getMetaData().getDatabaseProductName().toLowerCase().contains("postgresql");
    }
  }
}
