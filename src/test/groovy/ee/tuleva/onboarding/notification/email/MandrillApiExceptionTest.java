package ee.tuleva.onboarding.notification.email;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MandrillApiExceptionTest {

  @Test
  void anUnknownMessageErrorMeansMandrillNoLongerHoldsTheMessage() {
    var exception =
        new MandrillApiException(
            "/messages/cancel-scheduled.json",
            new MandrillError(500, 12, "Unknown_Message", "No message exists"));

    assertThat(exception.isUnknownMessage()).isTrue();
  }

  @Test
  void anyOtherErrorIsNotAnUnknownMessage() {
    var exception =
        new MandrillApiException(
            "/messages/cancel-scheduled.json",
            new MandrillError(500, -1, "Invalid_Key", "Invalid API key"));

    assertThat(exception.isUnknownMessage()).isFalse();
  }

  @Test
  void anUnreadableErrorIsNotAnUnknownMessage() {
    var exception =
        new MandrillApiException(
            "/messages/cancel-scheduled.json", new MandrillError(502, null, null, null));

    assertThat(exception.isUnknownMessage()).isFalse();
  }
}
