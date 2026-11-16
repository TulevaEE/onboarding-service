package ee.tuleva.onboarding.aml.alert;

import static ee.tuleva.onboarding.aml.alert.AmlAlertType.TKF_VOLUME_49K_YEARLY;
import static ee.tuleva.onboarding.aml.alert.TkfFlowDirection.COMBINED;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.jdbc.core.simple.JdbcClient;

@DataJpaTest
class AmlTkfVolumeAlertRepositoryTest {

  private static final Instant ALERTED_AT = Instant.parse("2026-09-01T03:35:00Z");

  @Autowired private AmlTkfVolumeAlertRepository repository;
  @Autowired private JdbcClient jdbcClient;

  @Test
  void loadsYearlyDepositAlertsRecordedBeforeTheCalendarYearAlertWasRemoved() {
    jdbcClient
        .sql(
            "INSERT INTO aml_tkf_volume_alert"
                + " (personal_id, alert_type, direction, window_key, alerted_at)"
                + " VALUES ('38888888888', 'TKF_VOLUME_49K_YEARLY', 'COMBINED', '2026', ?)")
        .param(Timestamp.from(ALERTED_AT))
        .update();

    assertThat(repository.findAll())
        .usingRecursiveFieldByFieldElementComparatorIgnoringFields("id")
        .containsExactly(
            AmlTkfVolumeAlert.builder()
                .personalId("38888888888")
                .alertType(TKF_VOLUME_49K_YEARLY)
                .direction(COMBINED)
                .windowKey("2026")
                .alertedAt(ALERTED_AT)
                .build());
  }
}
