package ee.tuleva.onboarding.notification.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class EmailServiceCancelScheduledEmailTest {

  private static final String ROOT = "https://mandrill.example.com/api/1.0";
  private static final String CANCEL_URL = ROOT + "/messages/cancel-scheduled.json";

  private final RestClient.Builder restClientBuilder = RestClient.builder().baseUrl(ROOT);
  private final MockRestServiceServer server =
      MockRestServiceServer.bindTo(restClientBuilder).build();
  private final EmailService emailService =
      new EmailService(
          new MandrillClient("test-key", restClientBuilder.build(), restClientBuilder.build()),
          MandrillConfiguration.emailServiceRetryTemplate());

  @Test
  void returnsEmptyWhenMandrillAnswersWithABodyThatCannotBeRead() {
    server
        .expect(requestTo(CANCEL_URL))
        .andRespond(withSuccess("<html>Maintenance</html>", APPLICATION_JSON));

    var cancelled = emailService.cancelScheduledEmail("abc123");

    server.verify();
    assertThat(cancelled).isEmpty();
  }

  @Test
  void returnsEmptyWhenMandrillAnswersWithAnEmptyBody() {
    server.expect(requestTo(CANCEL_URL)).andRespond(withSuccess("", APPLICATION_JSON));

    var cancelled = emailService.cancelScheduledEmail("abc123");

    server.verify();
    assertThat(cancelled).isEmpty();
  }

  @Test
  void returnsEmptyWhenTheConnectionToMandrillFails() {
    server
        .expect(requestTo(CANCEL_URL))
        .andRespond(withException(new IOException("Connection reset")));

    var cancelled = emailService.cancelScheduledEmail("abc123");

    server.verify();
    assertThat(cancelled).isEmpty();
  }
}
