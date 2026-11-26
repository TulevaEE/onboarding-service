package ee.tuleva.onboarding.notification.email;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MandrillErrorTest {

  @Test
  void readsMandrillsErrorAnswerTogetherWithTheHttpStatus() {
    var error =
        MandrillError.read(
            500,
            """
            {"status": "error", "code": 12, "name": "Unknown_Message", "message": "No message exists"}""");

    assertThat(error).isEqualTo(new MandrillError(500, 12, "Unknown_Message", "No message exists"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"\"invalid-key\"", "1.5", "99999999999", "null", "{}"})
  void aCodeThatIsNotAWholeNumberIsLeftOutWithoutLosingTheNameAndMessage(String code) {
    var error =
        MandrillError.read(
            500,
            """
            {"status": "error", "code": %s, "name": "Invalid_Key", "message": "Invalid API key"}"""
                .formatted(code));

    assertThat(error).isEqualTo(new MandrillError(500, null, "Invalid_Key", "Invalid API key"));
  }

  @Test
  void aMessageThatIsNotTextLeavesBothTheNameAndTheMessageOut() {
    var error =
        MandrillError.read(
            500,
            """
            {"status": "error", "code": -1, "name": "Invalid_Key", "message": {}}""");

    assertThat(error).isEqualTo(new MandrillError(500, -1, null, null));
  }

  @Test
  void aCodeTooLargeToReadIsLeftOutWithoutLosingTheNameAndMessage() {
    var error =
        MandrillError.read(
            500,
            """
            {"status": "error", "code": 1e2147483648, "name": "Invalid_Key", "message": "Invalid API key"}""");

    assertThat(error).isEqualTo(new MandrillError(500, null, "Invalid_Key", "Invalid API key"));
  }

  @Test
  void anAnswerWithoutAnyErrorFieldsKeepsOnlyTheHttpStatus() {
    var error = MandrillError.read(500, "{\"status\": \"error\"}");

    assertThat(error).isEqualTo(new MandrillError(500, null, null, null));
  }

  @ParameterizedTest
  @ValueSource(strings = {"<html>Bad gateway</html>", "", "\"just a string\"", "[1, 2]", "null"})
  void anAnswerThatIsNotAMandrillErrorKeepsOnlyTheHttpStatus(String body) {
    var error = MandrillError.read(502, body);

    assertThat(error).isEqualTo(new MandrillError(502, null, null, null));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"Invalid_Key", "PaymentRequired", "Unknown_Subaccount", "ValidationError"})
  void anErrorMandrillRaisesBeforeSendingMeansNothingWasSent(String name) {
    var error = new MandrillError(500, -1, name, "refused");

    assertThat(error.wasRaisedBeforeSending()).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"GeneralError", "Unknown_Message"})
  void anyOtherErrorMayHaveComeAfterSending(String name) {
    var error = new MandrillError(500, -1, name, "failed");

    assertThat(error.wasRaisedBeforeSending()).isFalse();
  }

  @Test
  void anUnreadableErrorMayHaveComeAfterSending() {
    var error = new MandrillError(502, null, null, null);

    assertThat(error.wasRaisedBeforeSending()).isFalse();
  }
}
