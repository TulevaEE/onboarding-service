package ee.tuleva.onboarding.auth.smartid;

import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.error.response.ErrorResponse;
import ee.tuleva.onboarding.error.response.ErrorsResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

class SmartIdExceptionTest {

  @Test
  void ofErrorsBuildsAnIndexedErrorForEachMessage() {
    var exception = SmartIdException.ofErrors(List.of("first error", "second error"));

    var expected = new ErrorsResponse();
    expected.add(ErrorResponse.builder().code("smart.id.error.0").message("first error").build());
    expected.add(ErrorResponse.builder().code("smart.id.error.1").message("second error").build());

    assertThat(exception.getErrorsResponse()).isEqualTo(expected);
  }

  @Test
  void ofErrorsWithNoMessagesBuildsAnEmptyErrorsResponse() {
    var exception = SmartIdException.ofErrors(List.of());

    assertThat(exception.getErrorsResponse()).isEqualTo(new ErrorsResponse());
  }
}
