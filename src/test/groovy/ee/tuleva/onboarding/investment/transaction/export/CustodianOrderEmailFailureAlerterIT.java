package ee.tuleva.onboarding.investment.transaction.export;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.TRADING;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.ERROR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.times;
import static org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED;

import ee.tuleva.onboarding.event.EventLog;
import ee.tuleva.onboarding.event.EventLogRepository;
import ee.tuleva.onboarding.mandate.email.webhook.MandrillSignatureVerifier;
import ee.tuleva.onboarding.mandate.email.webhook.MandrillWebhookService;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.notification.email.EmailPersistenceService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.json.JsonMapper;

@DataJpaTest
@Transactional(propagation = NOT_SUPPORTED)
@Import({
  MandrillWebhookService.class,
  CustodianOrderEmailFailureAlerter.class,
  CustodianOrderEmailFailureAlerterIT.WebhookJson.class
})
class CustodianOrderEmailFailureAlerterIT {

  private static final Instant BOUNCED_AT = Instant.parse("2026-01-15T10:01:00Z");
  private static final Instant A_MINUTE_LATER = BOUNCED_AT.plus(Duration.ofMinutes(1));
  private static final Instant TWO_MINUTES_LATER = BOUNCED_AT.plus(Duration.ofMinutes(2));
  private static final Instant PAST_THE_24_HOUR_RETRY_WINDOW =
      BOUNCED_AT.plus(Duration.ofHours(24)).plusSeconds(1);
  private static final String SIGNATURE = "valid-signature";
  private static final String SUBJECT = "TUV100 ostu-/müügikorraldused 15.01.2026";
  private static final String BOUNCE = "550 mailbox unavailable";
  private static final String RED_ALERT = redAlert("hard_bounce");
  private static final String AMBER_ALERT =
      "🟠 Order email delayed: to=desk@example.com, subject="
          + SUBJECT
          + "\ndeferral: 550 mailbox unavailable (id=msg_1)\n"
          + "Mandrill keeps trying. If the desk hasn't confirmed the orders before the cut-off,"
          + " check with the desk.";

  @Autowired MandrillWebhookService webhookService;
  @Autowired CustodianOrderEmailFailureAlerter alerter;
  @Autowired EventLogRepository eventLogRepository;

  @MockitoBean OperationsNotificationService notificationService;
  @MockitoBean MandrillSignatureVerifier signatureVerifier;
  @MockitoBean EmailPersistenceService emailPersistenceService;
  @MockitoBean Clock clock;

  @TestConfiguration
  static class WebhookJson {
    @Bean
    JsonMapper jsonMapper() {
      return JsonMapper.builder().build();
    }
  }

  @BeforeEach
  void setUp() {
    eventLogRepository.deleteAll();
    given(signatureVerifier.verify(any(), eq(SIGNATURE))).willReturn(true);
  }

  @AfterEach
  void cleanUp() {
    eventLogRepository.deleteAll();
  }

  @Test
  void theWebhookOnlyRecordsAnOrderEmailBounce_andTheNextRunPostsItOnce() {
    webhookReceives(orderEmailBounce("hard_bounce"));

    assertThat(eventLogRepository.findAll())
        .singleElement()
        .satisfies(row -> assertThat(row.getData()).isEqualTo(notYetPosted("hard_bounce")));
    then(notificationService).shouldHaveNoInteractions();

    runAt(A_MINUTE_LATER);
    runAt(TWO_MINUTES_LATER);

    then(notificationService).should().sendMessage(RED_ALERT, TRADING, ERROR);
    then(notificationService).shouldHaveNoMoreInteractions();
    assertThat(eventLogRepository.findAll())
        .singleElement()
        .satisfies(row -> assertThat(row.getData()).isEqualTo(posted("hard_bounce")));
  }

  @Test
  void aPostSlackDoesNotTakeStaysNotPosted_andTheNextRunPostsIt() {
    willThrow(new RestClientException("Slack is down"))
        .willDoNothing()
        .given(notificationService)
        .sendMessage(RED_ALERT, TRADING, ERROR);
    webhookReceives(orderEmailBounce("hard_bounce"));

    runAt(A_MINUTE_LATER);

    assertThat(eventLogRepository.findAll())
        .singleElement()
        .satisfies(row -> assertThat(row.getData()).isEqualTo(notYetPosted("hard_bounce")));

    runAt(TWO_MINUTES_LATER);

    then(notificationService).should(times(2)).sendMessage(RED_ALERT, TRADING, ERROR);
    assertThat(eventLogRepository.findAll())
        .singleElement()
        .satisfies(row -> assertThat(row.getData()).isEqualTo(posted("hard_bounce")));
  }

  @Test
  void aBounceMandrillSendsAgainIsRecordedAndPostedOnce() {
    webhookReceives(orderEmailBounce("hard_bounce"));
    webhookReceives(orderEmailBounce("hard_bounce"));

    runAt(A_MINUTE_LATER);

    then(notificationService).should().sendMessage(RED_ALERT, TRADING, ERROR);
    assertThat(eventLogRepository.findAll()).hasSize(1);
  }

  @ParameterizedTest
  @ValueSource(strings = {"hard_bounce", "soft_bounce", "reject", "deferral"})
  void theRunPostsEveryKindOfOrderEmailDeliveryFailure(String eventType) {
    webhookReceives(orderEmailBounce(eventType));

    runAt(A_MINUTE_LATER);

    then(notificationService).should().sendMessage(anyString(), eq(TRADING), any());
    assertThat(eventLogRepository.findAll())
        .singleElement()
        .satisfies(row -> assertThat(row.getData()).isEqualTo(posted(eventType)));
  }

  @Test
  void theRunLeavesTrusteeDeliveryFailureRowsAlone() {
    webhookReceives(trusteeEmailBounce());
    var trusteeRow =
        eventLogRepository.save(
            EventLog.builder()
                .type("HARD_BOUNCE")
                .principal("msg_2")
                .timestamp(BOUNCED_AT)
                .data(Map.of("recipient", "trustee@example.com"))
                .build());

    runAt(A_MINUTE_LATER);

    then(notificationService).shouldHaveNoInteractions();
    assertThat(eventLogRepository.findAll())
        .singleElement()
        .usingRecursiveComparison()
        .isEqualTo(trusteeRow);
  }

  @Test
  void aFailureSlackNeverTookIsGivenUpAtTheEndOfTheRetryWindow_andNotTriedAgain() {
    willThrow(new RestClientException("Slack is down"))
        .given(notificationService)
        .sendMessage(RED_ALERT, TRADING, ERROR);
    webhookReceives(orderEmailBounce("hard_bounce"));

    runAt(A_MINUTE_LATER);
    runAt(PAST_THE_24_HOUR_RETRY_WINDOW);
    runAt(PAST_THE_24_HOUR_RETRY_WINDOW.plus(Duration.ofMinutes(1)));

    then(notificationService).should().sendMessage(RED_ALERT, TRADING, ERROR);
    then(notificationService).shouldHaveNoMoreInteractions();
    assertThat(eventLogRepository.findAll())
        .singleElement()
        .satisfies(row -> assertThat(row.getData()).isEqualTo(givenUp("hard_bounce")));
  }

  private void webhookReceives(String mandrillEvents) {
    var request = new MockHttpServletRequest("POST", "/v1/emails/webhooks/mandrill");
    request.addParameter("mandrill_events", mandrillEvents);
    webhookService.handleWebhook(mandrillEvents, SIGNATURE, request);
  }

  private void runAt(Instant now) {
    given(clock.instant()).willReturn(now);
    alerter.postAlertsNotYetPosted();
  }

  private static String orderEmailBounce(String eventType) {
    return """
        [{"event": "%s", "ts": %d, "msg": {"_id": "msg_1", "email": "desk@example.com",
          "subject": "%s", "bounce_description": "%s", "tags": ["trade-order"]}}]
        """
        .formatted(eventType, BOUNCED_AT.getEpochSecond(), SUBJECT, BOUNCE);
  }

  private static String trusteeEmailBounce() {
    return """
        [{"event": "hard_bounce", "ts": %d, "msg": {"_id": "msg_2",
          "email": "trustee@example.com", "subject": "TKF100 NAV", "bounce_description": "%s",
          "tags": []}}]
        """
        .formatted(BOUNCED_AT.getEpochSecond(), BOUNCE);
  }

  private static Map<String, Object> notYetPosted(String eventType) {
    return failure(eventType, "NOT_POSTED");
  }

  private static Map<String, Object> posted(String eventType) {
    return failure(eventType, "POSTED");
  }

  private static Map<String, Object> givenUp(String eventType) {
    return failure(eventType, "GIVEN_UP");
  }

  private static Map<String, Object> failure(String eventType, String alert) {
    return "deferral".equals(eventType)
        ? Map.of("text", AMBER_ALERT, "severity", "WARNING", "alert", alert)
        : Map.of("text", redAlert(eventType), "severity", "ERROR", "alert", alert);
  }

  private static String redAlert(String eventType) {
    return "🔴 Order email NOT delivered: to=desk@example.com, subject="
        + SUBJECT
        + "\n"
        + eventType
        + ": 550 mailbox unavailable (id=msg_1)\n"
        + "The orders did not reach this address. Check with the desk before sending them"
        + " again.";
  }
}
