package ee.tuleva.onboarding.auth.response;

import static ee.tuleva.onboarding.auth.mobileid.MobileIdFixture.hash;
import static ee.tuleva.onboarding.auth.mobileid.MobileIdFixture.samplePhoneNumber;
import static ee.tuleva.onboarding.auth.mobileid.MobileIdFixture.sampleSessionId;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.auth.mobileid.MobileIDSession;
import org.junit.jupiter.api.Test;

class AuthenticateResponseTest {

  @Test
  void fromMobileIdSessionUsesTheChallengeAndAuthenticationHash() {
    var session = new MobileIDSession(sampleSessionId, "123456", hash, samplePhoneNumber);

    var response = AuthenticateResponse.fromMobileIdSession(session);

    assertThat(response.getChallengeCode()).isEqualTo("123456");
    assertThat(response.getAuthenticationHash()).isEqualTo(hash.getHashInBase64());
  }
}
