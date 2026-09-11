package ee.tuleva.onboarding.auth.mobileid;

import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.error.response.ErrorResponse;
import ee.tuleva.onboarding.error.response.ErrorsResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

class MobileIdExceptionTest {

  @Test
  void ofErrorsBuildsAnIndexedErrorForEachMessage() {
    var exception = MobileIdException.ofErrors(List.of("first error", "second error"));

    var expected = new ErrorsResponse();
    expected.add(ErrorResponse.builder().code("mobile.id.error.0").message("first error").build());
    expected.add(ErrorResponse.builder().code("mobile.id.error.1").message("second error").build());

    assertThat(exception.getErrorsResponse()).isEqualTo(expected);
  }

  @Test
  void ofErrorsWithNoMessagesBuildsAnEmptyErrorsResponse() {
    var exception = MobileIdException.ofErrors(List.of());

    assertThat(exception.getErrorsResponse()).isEqualTo(new ErrorsResponse());
  }
}
