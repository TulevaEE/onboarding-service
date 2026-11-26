package ee.tuleva.onboarding.notification.email;

import static com.microtripit.mandrillapp.lutung.view.MandrillMessage.Recipient.Type.TO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpStatus.ACCEPTED;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.TEXT_HTML;
import static org.springframework.test.json.JsonCompareMode.STRICT;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import com.microtripit.mandrillapp.lutung.view.MandrillMessage.Recipient;
import com.microtripit.mandrillapp.lutung.view.MandrillMessageStatus;
import com.microtripit.mandrillapp.lutung.view.MandrillScheduledMessageInfo;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class MandrillClientTest {

  private static final String ROOT = "https://mandrill.example.com/api/1.0";
  private static final String MESSAGE_JSON =
      """
      {"subject": "Hello", "to": [{"email": "someone@example.com", "type": "to"}]}""";
  private static final String SENT_STATUS_JSON =
      """
      [{"email": "someone@example.com", "status": "sent", "reject_reason": null, "_id": "abc123"}]""";

  private final RestClient.Builder whileAUserWaits = RestClient.builder().baseUrl(ROOT);
  private final RestClient.Builder forABackgroundJob = RestClient.builder().baseUrl(ROOT);
  private final MockRestServiceServer userWaitsServer =
      MockRestServiceServer.bindTo(whileAUserWaits).build();
  private final MockRestServiceServer backgroundJobServer =
      MockRestServiceServer.bindTo(forABackgroundJob).build();
  private final MandrillClient client =
      new MandrillClient("test-key", whileAUserWaits.build(), forABackgroundJob.build());

  @Test
  void sendPostsTheMessageWithTheKeyForSynchronousDeliveryAndReturnsTheRecipientStatuses() {
    backgroundJobServer
        .expect(requestTo(ROOT + "/messages/send.json"))
        .andExpect(method(POST))
        .andExpect(content().contentType(APPLICATION_JSON))
        .andExpect(
            content()
                .json(
                    """
                    {"key": "test-key", "message": %s, "async": false}"""
                        .formatted(MESSAGE_JSON),
                    STRICT))
        .andRespond(withSuccess(SENT_STATUS_JSON, APPLICATION_JSON));

    MandrillMessageStatus[] statuses = client.send(message());

    backgroundJobServer.verify();
    assertSentStatus(statuses);
  }

  @Test
  void sendTemplatePostsTheTemplateWithTheValidationPlaceholderContentAndNoSendTime() {
    userWaitsServer
        .expect(requestTo(ROOT + "/messages/send-template.json"))
        .andExpect(method(POST))
        .andExpect(content().contentType(APPLICATION_JSON))
        .andExpect(
            content()
                .json(
                    """
                    {
                      "key": "test-key",
                      "template_name": "welcome",
                      "template_content": [{"name": "satisfy_validation", "content": ""}],
                      "message": %s,
                      "async": false
                    }"""
                        .formatted(MESSAGE_JSON),
                    STRICT))
        .andRespond(withSuccess(SENT_STATUS_JSON, APPLICATION_JSON));

    MandrillMessageStatus[] statuses = client.sendTemplate("welcome", message(), null);

    userWaitsServer.verify();
    assertSentStatus(statuses);
  }

  @Test
  void sendTemplateFromBackgroundJobPostsTheSameTemplateRequestWithoutASendTime() {
    backgroundJobServer
        .expect(requestTo(ROOT + "/messages/send-template.json"))
        .andExpect(method(POST))
        .andExpect(content().contentType(APPLICATION_JSON))
        .andExpect(
            content()
                .json(
                    """
                    {
                      "key": "test-key",
                      "template_name": "welcome",
                      "template_content": [{"name": "satisfy_validation", "content": ""}],
                      "message": %s,
                      "async": false
                    }"""
                        .formatted(MESSAGE_JSON),
                    STRICT))
        .andRespond(withSuccess(SENT_STATUS_JSON, APPLICATION_JSON));

    MandrillMessageStatus[] statuses = client.sendTemplateFromBackgroundJob("welcome", message());

    backgroundJobServer.verify();
    assertSentStatus(statuses);
  }

  @Test
  void sendTemplateSchedulesTheMessageAtTheSendTimeInUtcToTheSecond() {
    userWaitsServer
        .expect(requestTo(ROOT + "/messages/send-template.json"))
        .andExpect(
            content()
                .json(
                    """
                    {
                      "key": "test-key",
                      "template_name": "welcome",
                      "template_content": [{"name": "satisfy_validation", "content": ""}],
                      "message": %s,
                      "async": false,
                      "send_at": "2026-11-25 21:30:15"
                    }"""
                        .formatted(MESSAGE_JSON),
                    STRICT))
        .andRespond(withSuccess(SENT_STATUS_JSON, APPLICATION_JSON));

    MandrillMessageStatus[] statuses =
        client.sendTemplate("welcome", message(), Instant.parse("2026-11-25T23:30:15.789+02:00"));

    userWaitsServer.verify();
    assertSentStatus(statuses);
  }

  @Test
  void cancelScheduledPostsTheMessageIdAndReturnsTheCancelledMessage() {
    userWaitsServer
        .expect(requestTo(ROOT + "/messages/cancel-scheduled.json"))
        .andExpect(method(POST))
        .andExpect(content().contentType(APPLICATION_JSON))
        .andExpect(
            content()
                .json(
                    """
            {"key": "test-key", "id": "abc123"}""",
                    STRICT))
        .andRespond(
            withSuccess(
                """
                {
                  "_id": "abc123",
                  "created_at": "2026-11-20 08:00:00",
                  "send_at": "2026-11-25 21:30:15",
                  "from_email": "tuleva@example.com",
                  "to": "someone@example.com",
                  "subject": "Hello"
                }""",
                APPLICATION_JSON));

    MandrillScheduledMessageInfo info = client.cancelScheduled("abc123");

    userWaitsServer.verify();
    assertThat(info)
        .extracting(
            MandrillScheduledMessageInfo::getId,
            MandrillScheduledMessageInfo::getCreated_at,
            MandrillScheduledMessageInfo::getSend_at,
            MandrillScheduledMessageInfo::getFromEmail,
            MandrillScheduledMessageInfo::getTo,
            MandrillScheduledMessageInfo::getSubject)
        .containsExactly(
            "abc123",
            Date.from(Instant.parse("2026-11-20T08:00:00Z")),
            Date.from(Instant.parse("2026-11-25T21:30:15Z")),
            "tuleva@example.com",
            "someone@example.com",
            "Hello");
  }

  @Test
  void mandrillErrorAnswerBecomesAnExceptionCarryingTheMandrillError() {
    userWaitsServer
        .expect(requestTo(ROOT + "/messages/cancel-scheduled.json"))
        .andRespond(
            withStatus(INTERNAL_SERVER_ERROR)
                .contentType(APPLICATION_JSON)
                .body(
                    """
                    {
                      "status": "error",
                      "code": 12,
                      "name": "Unknown_Message",
                      "message": "No message exists with the id 'abc123'"
                    }"""));

    MandrillApiException exception =
        catchThrowableOfType(MandrillApiException.class, () -> client.cancelScheduled("abc123"));

    assertThat(exception.getError())
        .isEqualTo(
            new MandrillError(
                500, 12, "Unknown_Message", "No message exists with the id 'abc123'"));
  }

  @Test
  void unreadableErrorAnswerBecomesAnExceptionWithoutAnErrorName() {
    backgroundJobServer
        .expect(requestTo(ROOT + "/messages/send.json"))
        .andRespond(
            withStatus(BAD_GATEWAY).contentType(TEXT_HTML).body("<html>Bad gateway</html>"));

    MandrillApiException exception =
        catchThrowableOfType(MandrillApiException.class, () -> client.send(message()));

    assertThat(exception.getError()).isEqualTo(new MandrillError(502, null, null, null));
  }

  @Test
  void successOtherThanOkIsTreatedAsAMandrillError() {
    backgroundJobServer
        .expect(requestTo(ROOT + "/messages/send.json"))
        .andRespond(withStatus(ACCEPTED));

    MandrillApiException exception =
        catchThrowableOfType(MandrillApiException.class, () -> client.send(message()));

    assertThat(exception.getError()).isEqualTo(new MandrillError(202, null, null, null));
  }

  @Test
  void emptyOkAnswerIsAnError() {
    backgroundJobServer
        .expect(requestTo(ROOT + "/messages/send.json"))
        .andRespond(withSuccess("", APPLICATION_JSON));

    assertThatThrownBy(() -> client.send(message())).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void callsAUserWaitsForGoThroughTheirClientAndCallsFromABackgroundJobThroughTheOther() {
    userWaitsServer
        .expect(requestTo(ROOT + "/messages/send-template.json"))
        .andRespond(withSuccess(SENT_STATUS_JSON, APPLICATION_JSON));
    userWaitsServer
        .expect(requestTo(ROOT + "/messages/cancel-scheduled.json"))
        .andRespond(
            withSuccess(
                """
            {"_id": "abc123"}""",
                APPLICATION_JSON));
    backgroundJobServer
        .expect(requestTo(ROOT + "/messages/send-template.json"))
        .andRespond(withSuccess(SENT_STATUS_JSON, APPLICATION_JSON));
    backgroundJobServer
        .expect(requestTo(ROOT + "/messages/send.json"))
        .andRespond(withSuccess(SENT_STATUS_JSON, APPLICATION_JSON));

    client.sendTemplate("welcome", message(), null);
    client.cancelScheduled("abc123");
    client.sendTemplateFromBackgroundJob("welcome", message());
    client.send(message());

    userWaitsServer.verify();
    backgroundJobServer.verify();
  }

  private static MandrillMessage message() {
    var recipient = new Recipient();
    recipient.setEmail("someone@example.com");
    recipient.setType(TO);
    var message = new MandrillMessage();
    message.setSubject("Hello");
    message.setTo(List.of(recipient));
    return message;
  }

  private static void assertSentStatus(MandrillMessageStatus[] statuses) {
    assertThat(statuses)
        .extracting(
            MandrillMessageStatus::getEmail,
            MandrillMessageStatus::getStatus,
            MandrillMessageStatus::getRejectReason,
            MandrillMessageStatus::getId)
        .containsExactly(tuple("someone@example.com", "sent", null, "abc123"));
  }
}
