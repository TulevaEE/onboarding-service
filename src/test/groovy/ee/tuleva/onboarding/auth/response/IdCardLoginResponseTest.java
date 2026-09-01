package ee.tuleva.onboarding.auth.response;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IdCardLoginResponseTest {

  @Test
  void successBuildsAResponseWithSuccessTrue() {
    var response = IdCardLoginResponse.success();

    assertThat(response).isEqualTo(IdCardLoginResponse.builder().success(true).build());
  }
}
