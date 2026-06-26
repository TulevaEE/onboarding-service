package ee.tuleva.onboarding.aml.alert;

import static ee.tuleva.onboarding.aml.alert.AmlAlertType.TKF_VOLUME_15K_NEW_CLIENT;
import static ee.tuleva.onboarding.aml.alert.TkfFlowDirection.IN;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.AML;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class TkfVolumeAlertIntegrationTest {

  @Autowired private TkfVolumeAlertService tkfVolumeAlertService;
  @Autowired private AmlTkfVolumeAlertRepository alertRepository;
  @Autowired private JdbcClient jdbcClient;
  @Autowired private Clock clock;

  @MockitoBean private OperationsNotificationService notificationService;

  @Test
  @DisplayName(
      "alerts a new client's >=15k monthly TKF deposit exactly once to the AML channel and is idempotent")
  void newClientMonthlyDeposit_alertsOnceAndIsIdempotent() {
    String personalId = "38001019999";
    BigDecimal amount = new BigDecimal("15123.45");
    String monthKey = LocalDate.now(clock).format(DateTimeFormatter.ofPattern("yyyy-MM"));

    jdbcClient
        .sql(
            "INSERT INTO saving_fund_payment"
                + " (id, external_id, amount, currency, status, party_type, party_code, created_at)"
                + " VALUES (?, ?, ?, 'EUR', 'PROCESSED', 'PERSON', ?, ?)")
        .params(
            UUID.randomUUID(),
            "ext-" + UUID.randomUUID(),
            amount,
            personalId,
            Timestamp.from(Instant.now()))
        .update();
    jdbcClient
        .sql(
            "INSERT INTO analytics.mv_crm"
                + " (personal_id, balance_in_third_pillar, balance_in_tuk75, balance_in_tuk00)"
                + " VALUES (?, false, false, false)")
        .param(personalId)
        .update();

    tkfVolumeAlertService.checkAndAlert();

    verify(notificationService, times(1))
        .sendMessage(
            argThat(
                message ->
                    message.startsWith("AML alert: TKF_VOLUME_15K_NEW_CLIENT")
                        && message.contains("amount=15123.45")
                        && message.contains("ref=IN/" + monthKey)),
            eq(AML));
    assertThat(
            alertRepository.existsByPersonalIdAndAlertTypeAndDirectionAndWindowKey(
                personalId, TKF_VOLUME_15K_NEW_CLIENT, IN, monthKey))
        .isTrue();

    tkfVolumeAlertService.checkAndAlert();

    verify(notificationService, times(1))
        .sendMessage(argThat(message -> message.contains("amount=15123.45")), eq(AML));
  }
}
