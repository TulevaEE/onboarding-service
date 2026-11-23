package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.web.client.RestClientException;

@ExtendWith(MockitoExtension.class)
class HealthCheckAlertSenderTest {

  private static final LocalDate REPORT_DATE = LocalDate.of(2026, 10, 30);
  private static final Instant FOUND_AT = Instant.parse("2026-10-31T07:00:00Z");
  private static final long ALERT_ID = 7L;
  private static final String MESSAGE =
      "Import warning: SEB 2026-10-30 — TUV100 not imported\nFunds: TUV100 not in the report";

  @Mock OperationsNotificationService notificationService;
  @Mock HealthCheckAlertRepository alertRepository;

  HealthCheckAlertSender sender;

  @BeforeEach
  void setUp() {
    sender =
        new HealthCheckAlertSender(
            notificationService, alertRepository, Clock.fixed(FOUND_AT, UTC));
  }

  @Test
  void anAlertIsRecordedBeforeItIsPostedAndMarkedPostedAfter() {
    given(alertRepository.recordNotPosted(SEB, REPORT_DATE, MESSAGE, FOUND_AT))
        .willReturn(ALERT_ID);

    var raised = sender.send(SEB, REPORT_DATE, MESSAGE);

    assertThat(raised).isTrue();
    var order = inOrder(alertRepository, notificationService);
    order.verify(alertRepository).recordNotPosted(SEB, REPORT_DATE, MESSAGE, FOUND_AT);
    order.verify(notificationService).sendMessage(MESSAGE, INVESTMENT);
    order.verify(alertRepository).markPosted(ALERT_ID, FOUND_AT);
  }

  @Test
  void anAlertSlackDoesNotTakeStaysNotPostedForTheRetryRun() {
    given(alertRepository.recordNotPosted(SEB, REPORT_DATE, MESSAGE, FOUND_AT))
        .willReturn(ALERT_ID);
    willThrow(new RestClientException("Slack is down"))
        .given(notificationService)
        .sendMessage(MESSAGE, INVESTMENT);

    var raised = sender.send(SEB, REPORT_DATE, MESSAGE);

    assertThat(raised).isTrue();
    then(alertRepository).should(never()).markPosted(anyLong(), any());
  }

  @Test
  void anAlertThatCannotBeRecordedIsStillPostedRightAway() {
    given(alertRepository.recordNotPosted(SEB, REPORT_DATE, MESSAGE, FOUND_AT))
        .willThrow(new DataAccessResourceFailureException("database down"));

    var raised = sender.send(SEB, REPORT_DATE, MESSAGE);

    assertThat(raised).isTrue();
    then(notificationService).should().sendMessage(MESSAGE, INVESTMENT);
    then(alertRepository).should(never()).markPosted(anyLong(), any());
  }

  @Test
  void anAlertThatCanBeNeitherRecordedNorPostedIsNotRaised() {
    given(alertRepository.recordNotPosted(SEB, REPORT_DATE, MESSAGE, FOUND_AT))
        .willThrow(new DataAccessResourceFailureException("database down"));
    willThrow(new RestClientException("Slack is down"))
        .given(notificationService)
        .sendMessage(MESSAGE, INVESTMENT);

    var raised = sender.send(SEB, REPORT_DATE, MESSAGE);

    assertThat(raised).isFalse();
  }

  @Test
  void aPostedAlertThatCannotBeMarkedPostedIsStillRaised() {
    given(alertRepository.recordNotPosted(SEB, REPORT_DATE, MESSAGE, FOUND_AT))
        .willReturn(ALERT_ID);
    given(alertRepository.markPosted(ALERT_ID, FOUND_AT))
        .willThrow(new DataAccessResourceFailureException("database down"));

    var raised = sender.send(SEB, REPORT_DATE, MESSAGE);

    assertThat(raised).isTrue();
    then(notificationService).should().sendMessage(MESSAGE, INVESTMENT);
  }
}
