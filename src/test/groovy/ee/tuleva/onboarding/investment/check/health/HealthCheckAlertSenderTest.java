package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.web.client.ResourceAccessException;

@ExtendWith(MockitoExtension.class)
class HealthCheckAlertSenderTest {

  private static final LocalDate REPORT_DATE = LocalDate.of(2026, 10, 30);
  private static final Instant FOUND_AT = Instant.parse("2026-10-31T07:00:00Z");
  private static final String MESSAGE =
      "Import warning: SEB 2026-10-30 — TUV100 not imported\nFunds: TUV100 not in the report";
  private static final RuntimeException CONNECTION_REFUSED =
      new ResourceAccessException("I/O error", new ConnectException("Connection refused"));
  private static final RuntimeException NO_ANSWER =
      new ResourceAccessException("I/O error", new SocketTimeoutException("Read timed out"));

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
  void anAlertSlackTakesIsPostedRightAwayAndLeavesNoRow() {
    var raised = sender.send(SEB, REPORT_DATE, MESSAGE);

    assertThat(raised).isTrue();
    then(notificationService).should().sendMessage(MESSAGE, INVESTMENT);
    then(alertRepository).should().anyNotPosted();
    then(alertRepository).shouldHaveNoMoreInteractions();
  }

  @Test
  void anAlertSlackDoesNotTakeIsLeftForTheRetryRunWithTheTimeItsStateWasFound() {
    willThrow(CONNECTION_REFUSED).given(notificationService).sendMessage(MESSAGE, INVESTMENT);

    var raised = sender.send(SEB, REPORT_DATE, MESSAGE);

    assertThat(raised).isTrue();
    then(alertRepository).should().recordNotPosted(SEB, REPORT_DATE, MESSAGE, FOUND_AT, 0);
  }

  @Test
  void anAlertSlackGaveNoAnswerToIsLeftForTheRetryRunWithOneUnansweredPost() {
    willThrow(NO_ANSWER).given(notificationService).sendMessage(MESSAGE, INVESTMENT);

    var raised = sender.send(SEB, REPORT_DATE, MESSAGE);

    assertThat(raised).isTrue();
    then(alertRepository).should().recordNotPosted(SEB, REPORT_DATE, MESSAGE, FOUND_AT, 1);
  }

  @Test
  void anAlertFoundWhileOthersWaitIsQueuedBehindThemWithoutBeingPosted() {
    given(alertRepository.anyNotPosted()).willReturn(true);

    var raised = sender.send(SEB, REPORT_DATE, MESSAGE);

    assertThat(raised).isTrue();
    then(alertRepository).should().recordNotPosted(SEB, REPORT_DATE, MESSAGE, FOUND_AT, 0);
    then(notificationService).shouldHaveNoInteractions();
  }

  @Test
  void anAlertThatCannotBeQueuedBehindOthersIsPostedRightAway() {
    given(alertRepository.anyNotPosted()).willReturn(true);
    given(alertRepository.recordNotPosted(SEB, REPORT_DATE, MESSAGE, FOUND_AT, 0))
        .willThrow(new DataAccessResourceFailureException("database down"));

    var raised = sender.send(SEB, REPORT_DATE, MESSAGE);

    assertThat(raised).isTrue();
    then(notificationService).should().sendMessage(MESSAGE, INVESTMENT);
  }

  @Test
  void anAlertWhoseQueueCannotBeReadIsPostedRightAway() {
    given(alertRepository.anyNotPosted())
        .willThrow(new DataAccessResourceFailureException("database down"));

    var raised = sender.send(SEB, REPORT_DATE, MESSAGE);

    assertThat(raised).isTrue();
    then(notificationService).should().sendMessage(MESSAGE, INVESTMENT);
  }

  @Test
  void anAlertThatCanBeNeitherPostedNorRecordedIsNotRaised() {
    willThrow(CONNECTION_REFUSED).given(notificationService).sendMessage(MESSAGE, INVESTMENT);
    given(alertRepository.recordNotPosted(SEB, REPORT_DATE, MESSAGE, FOUND_AT, 0))
        .willThrow(new DataAccessResourceFailureException("database down"));

    var raised = sender.send(SEB, REPORT_DATE, MESSAGE);

    assertThat(raised).isFalse();
  }
}
