package ee.tuleva.onboarding.investment.transaction.export;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.TRADING;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.ERROR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.event.EventLog;
import ee.tuleva.onboarding.event.EventLogRepository;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.notification.email.EmailDeliveryFailedEvent;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.web.client.RestClientException;

@DataJpaTest
class CustodianOrderEmailFailureAlerterIT {

  private static final Instant BOUNCED_AT = Instant.parse("2026-01-15T10:01:00Z");
  private static final String SUBJECT = "TUV100 ostu-/müügikorraldused 15.01.2026";
  private static final String RED_ALERT =
      "🔴 Order email NOT delivered: to=desk@example.com, subject="
          + SUBJECT
          + "\nhard_bounce: 550 mailbox unavailable (id=msg_1)\n"
          + "The orders did not reach this address. Check with the desk before sending them"
          + " again.";

  @Autowired EventLogRepository eventLogRepository;
  @Autowired TestEntityManager entityManager;

  private final OperationsNotificationService notificationService =
      mock(OperationsNotificationService.class);
  private CustodianOrderEmailFailureAlerter alerter;

  @BeforeEach
  void setUp() {
    eventLogRepository.deleteAll();
    alerter = new CustodianOrderEmailFailureAlerter(notificationService, eventLogRepository);
  }

  @Test
  void anOrderEmailBounceThatArrivesWhileSlackIsDownIsPostedByTheNextRetryRunWithoutAnotherEvent() {
    willThrow(new RestClientException("Slack is down"))
        .willDoNothing()
        .given(notificationService)
        .sendMessage(RED_ALERT, TRADING, ERROR);

    receive(orderEmailFailure("hard_bounce"));
    assertThat(eventLogRepository.findAll()).singleElement().satisfies(notYetAlerted());

    retryRun();
    retryRun();

    verify(notificationService, times(2)).sendMessage(RED_ALERT, TRADING, ERROR);
    assertThat(eventLogRepository.findAll()).singleElement().satisfies(alerted());
  }

  @Test
  void aBounceMandrillSendsAgainWhileSlackIsDownIsStillPostedOnceSlackIsBack() {
    willThrow(new RestClientException("Slack is down"))
        .willThrow(new RestClientException("Slack is down"))
        .willDoNothing()
        .given(notificationService)
        .sendMessage(RED_ALERT, TRADING, ERROR);

    receive(orderEmailFailure("hard_bounce"));
    receive(orderEmailFailure("hard_bounce"));
    retryRun();
    retryRun();
    retryRun();

    verify(notificationService, times(3)).sendMessage(RED_ALERT, TRADING, ERROR);
    assertThat(eventLogRepository.findAll()).singleElement().satisfies(alerted());
  }

  @ParameterizedTest
  @ValueSource(strings = {"hard_bounce", "soft_bounce", "reject", "deferral"})
  void theRetryRunPostsEveryKindOfDeliveryFailureSlackMissed(String eventType) {
    willThrow(new RestClientException("Slack is down"))
        .willDoNothing()
        .given(notificationService)
        .sendMessage(anyString(), any(), any());

    receive(orderEmailFailure(eventType));
    retryRun();

    verify(notificationService, times(2)).sendMessage(anyString(), any(), any());
    assertThat(eventLogRepository.findAll()).singleElement().satisfies(alerted());
  }

  @Test
  void theRetryRunDoesNotRepostAnAlertThatWasPostedWhenTheBounceArrived() {
    willDoNothing().given(notificationService).sendMessage(RED_ALERT, TRADING, ERROR);

    receive(orderEmailFailure("hard_bounce"));
    retryRun();

    verify(notificationService, times(1)).sendMessage(RED_ALERT, TRADING, ERROR);
  }

  @Test
  void theRetryRunLeavesOtherDeliveryFailureRecordsAlone() {
    eventLogRepository.save(
        EventLog.builder()
            .type("HARD_BOUNCE")
            .principal("msg_2")
            .timestamp(BOUNCED_AT)
            .data(Map.of("recipient", "trustee@example.com"))
            .build());

    retryRun();

    verify(notificationService, never()).sendMessage(anyString(), any(), any());
  }

  private void receive(EmailDeliveryFailedEvent event) {
    alerter.onEmailDeliveryFailed(event);
    entityManager.flush();
    entityManager.clear();
  }

  private void retryRun() {
    alerter.alertFailuresSlackMissed();
    entityManager.flush();
    entityManager.clear();
  }

  private static EmailDeliveryFailedEvent orderEmailFailure(String eventType) {
    return new EmailDeliveryFailedEvent(
        "desk@example.com",
        SUBJECT,
        eventType,
        "550 mailbox unavailable",
        "msg_1",
        BOUNCED_AT,
        List.of("trade-order"));
  }

  private static Consumer<EventLog> notYetAlerted() {
    return failure -> assertThat(failure.getData()).containsEntry("alertPosted", false);
  }

  private static Consumer<EventLog> alerted() {
    return failure -> assertThat(failure.getData()).containsEntry("alertPosted", true);
  }
}
