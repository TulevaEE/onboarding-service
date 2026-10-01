package ee.tuleva.onboarding.auth.mobileid;

import static ee.tuleva.onboarding.auth.browser.PushLogin.MOBILE_ID;
import static ee.tuleva.onboarding.auth.mobileid.MobileIdFixture.sampleMobileIdSession;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.auth.browser.PushLoginStartedTooSoonException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class MobileIdLoginStarterTest {

  private static final String PERSONAL_CODE = "38888888888";
  private static final String REMEMBERED_PHONE = "+37255555555";

  private final MobileIdAuthService authService = mock(MobileIdAuthService.class);
  private final RememberedMobileIdPhones rememberedPhones = mock(RememberedMobileIdPhones.class);
  private final MobileIdLoginStarter starter =
      new MobileIdLoginStarter(authService, rememberedPhones);

  @Test
  void aTypedPhoneAlwaysWinsAndIsNotThrottled() {
    given(authService.startLogin("5123 4567", PERSONAL_CODE)).willReturn(sampleMobileIdSession);

    MobileIDSession session = starter.start("5123 4567", PERSONAL_CODE);

    assertThat(session).isSameAs(sampleMobileIdSession);
    verifyNoInteractions(rememberedPhones);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void withoutATypedPhoneUsesThePhoneThisBrowserRemembersForThePerson(String typed) {
    var session =
        new MobileIDSession("mid-session", "1234", MobileIdFixture.hash, REMEMBERED_PHONE);
    given(rememberedPhones.find(PERSONAL_CODE))
        .willReturn(Optional.of(new RememberedMobileIdPhone(3L, REMEMBERED_PHONE)));
    given(authService.startLogin(REMEMBERED_PHONE, PERSONAL_CODE)).willReturn(session);

    assertThat(starter.start(typed, PERSONAL_CODE)).isSameAs(session);
    verify(rememberedPhones).claimLoginStart();
  }

  @Test
  void withoutATypedPhoneOrARememberedOneAsksForThePhone() {
    given(rememberedPhones.find(PERSONAL_CODE)).willReturn(Optional.empty());

    assertThatThrownBy(() -> starter.start(null, PERSONAL_CODE))
        .isInstanceOf(MobileIdException.class)
        .extracting(
            e -> ((MobileIdException) e).getErrorsResponse().getErrors().getFirst().getCode())
        .isEqualTo("mobile.id.phone.number.required");
    verify(authService, never()).startLogin(any(), any());
  }

  @Test
  void aRememberedPhoneIsNotPushedToAgainWithinThirtySeconds() {
    given(rememberedPhones.find(PERSONAL_CODE))
        .willReturn(Optional.of(new RememberedMobileIdPhone(3L, REMEMBERED_PHONE)));
    willThrow(new PushLoginStartedTooSoonException(MOBILE_ID))
        .given(rememberedPhones)
        .claimLoginStart();

    assertThatThrownBy(() -> starter.start("", PERSONAL_CODE))
        .isInstanceOf(PushLoginStartedTooSoonException.class);
    verify(authService, never()).startLogin(any(), any());
  }
}
