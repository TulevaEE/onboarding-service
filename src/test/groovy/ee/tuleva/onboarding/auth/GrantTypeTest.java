package ee.tuleva.onboarding.auth;

import static ee.tuleva.onboarding.auth.GrantType.PARTNER;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class GrantTypeTest {

  @ParameterizedTest
  @EnumSource(names = {"ID_CARD", "MOBILE_ID", "SMART_ID"})
  void anIdCardMobileIdOrSmartIdLoginIsAStrongLogin(GrantType grantType) {
    assertThat(grantType.isStrongLogin()).isTrue();
  }

  @Test
  void aPartnerHandoverIsNotAStrongLogin() {
    assertThat(PARTNER.isStrongLogin()).isFalse();
  }
}
