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
class HealthCheckAlertOutboxTest {

  private static final LocalDate REPORT_DATE = LocalDate.of(2026, 10, 30);
  private static final Instant FOUND_AT = Instant.parse("2026-10-31T07:00:00Z");
  private static final String MESSAGE =
      "Import warning: SEB 2026-10-30 — TUV100 not imported\nFunds: TUV100 not in the report";

  @Mock OperationsNotificationService notificationService;
  @Mock HealthCheckAlertRepository alertRepository;

  HealthCheckAlertOutbox outbox;

  @BeforeEach
  void setUp() {
    outbox =
        new HealthCheckAlertOutbox(
            notificationService, alertRepository, Clock.fixed(FOUND_AT, UTC));
  }

  @Test
  void raisingAnAlertSavesItWithTheTimeItsStateWasFoundAndPostsNothing() {
    var raised = outbox.raise(SEB, REPORT_DATE, MESSAGE);

    assertThat(raised).isTrue();
    then(alertRepository).should().recordNotPosted(SEB, REPORT_DATE, MESSAGE, FOUND_AT);
    then(notificationService).shouldHaveNoInteractions();
  }

  @Test
  void anAlertThatCannotBeSavedIsPostedRightAwayOnce() {
    given(alertRepository.recordNotPosted(SEB, REPORT_DATE, MESSAGE, FOUND_AT))
        .willThrow(new DataAccessResourceFailureException("database down"));

    var raised = outbox.raise(SEB, REPORT_DATE, MESSAGE);

    assertThat(raised).isTrue();
    then(notificationService).should().sendMessage(MESSAGE, INVESTMENT);
    then(notificationService).shouldHaveNoMoreInteractions();
  }

  @Test
  void anAlertThatCanBeNeitherSavedNorPostedIsNotRaised() {
    given(alertRepository.recordNotPosted(SEB, REPORT_DATE, MESSAGE, FOUND_AT))
        .willThrow(new DataAccessResourceFailureException("database down"));
    willThrow(new ResourceAccessException("I/O error", new ConnectException("Connection refused")))
        .given(notificationService)
        .sendMessage(MESSAGE, INVESTMENT);

    var raised = outbox.raise(SEB, REPORT_DATE, MESSAGE);

    assertThat(raised).isFalse();
  }
}
