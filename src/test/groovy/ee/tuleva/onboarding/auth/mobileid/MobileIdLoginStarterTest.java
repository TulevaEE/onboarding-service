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
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class MobileIdLoginStarterTest {

  private static final String PERSONAL_CODE = "38888888888";
  private static final String REMEMBERED_PHONE = "+37255555555";
  private static final Instant CLAIMED_AT = Instant.parse("2026-10-02T10:00:00Z");

  private final MobileIdAuthService authService = mock(MobileIdAuthService.class);
  private final RememberedMobileIdPhones rememberedPhones = mock(RememberedMobileIdPhones.class);
  private final MobileIdLoginStarter starter =
      new MobileIdLoginStarter(authService, rememberedPhones);

  @Test
  void aTypedPhoneAlwaysWinsAndIsNotThrottled() {
    given(authService.startLogin("5123 4567", PERSONAL_CODE)).willReturn(sampleMobileIdSession);

    MobileIDSession session = starter.start("5123 4567", PERSONAL_CODE, false);

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
    given(rememberedPhones.claimLoginStart()).willReturn(CLAIMED_AT);
    given(authService.startLogin(REMEMBERED_PHONE, PERSONAL_CODE)).willReturn(session);

    assertThat(starter.start(typed, PERSONAL_CODE, false)).isSameAs(session);
    assertThat(session.getRememberedPhoneId()).isEqualTo(3L);
    assertThat(session.getPushLoginClaimedAt()).isEqualTo(CLAIMED_AT);
  }

  @Test
  void continuingAsTheRememberedPersonStartsWithTheirIdentityCodeAndPhoneAndKeepsRememberingThem() {
    var session =
        new MobileIDSession("mid-session", "1234", MobileIdFixture.hash, REMEMBERED_PHONE);
    given(rememberedPhones.mostRecentPerson())
        .willReturn(
            Optional.of(
                new RememberedMobileIdPerson(
                    PERSONAL_CODE, "AADU", new RememberedMobileIdPhone(3L, REMEMBERED_PHONE))));
    given(authService.startLogin(REMEMBERED_PHONE, PERSONAL_CODE)).willReturn(session);

    assertThat(starter.startForRememberedPerson()).isSameAs(session);
    assertThat(session.getRememberedPhoneId()).isEqualTo(3L);
    assertThat(session.isRememberMe()).isTrue();
    verify(rememberedPhones).claimLoginStart();
  }

  @Test
  void continuingAsARememberedPersonOnABrowserThatRemembersNobodyAsksForThePhone() {
    given(rememberedPhones.mostRecentPerson()).willReturn(Optional.empty());

    assertThatThrownBy(starter::startForRememberedPerson).isInstanceOf(MobileIdException.class);
    verify(authService, never()).startLogin(any(), any());
  }

  @Test
  void continuingAsARememberedPersonWhosePhoneIsNoLongerTheirsForgetsItAndAsksForThePhone() {
    given(rememberedPhones.mostRecentPerson())
        .willReturn(
            Optional.of(
                new RememberedMobileIdPerson(
                    PERSONAL_CODE, "AADU", new RememberedMobileIdPhone(3L, REMEMBERED_PHONE))));
    given(authService.startLogin(REMEMBERED_PHONE, PERSONAL_CODE))
        .willThrow(new MobileIdNotMidClientException());

    assertThatThrownBy(starter::startForRememberedPerson).isInstanceOf(MobileIdException.class);
    verify(rememberedPhones).forget(3L);
  }

  @Test
  void aSessionStartedWithATypedPhoneKeepsWhetherThePersonAskedToBeRemembered() {
    var session = new MobileIDSession("mid-session", "1234", MobileIdFixture.hash, "+37251234567");
    given(authService.startLogin("5123 4567", PERSONAL_CODE)).willReturn(session);

    assertThat(starter.start("5123 4567", PERSONAL_CODE, true).isRememberMe()).isTrue();
  }

  @Test
  void aSessionStartedWithTheRememberedPhoneKeepsWhetherThePersonAskedToBeRemembered() {
    var session =
        new MobileIDSession("mid-session", "1234", MobileIdFixture.hash, REMEMBERED_PHONE);
    given(rememberedPhones.find(PERSONAL_CODE))
        .willReturn(Optional.of(new RememberedMobileIdPhone(3L, REMEMBERED_PHONE)));
    given(authService.startLogin(REMEMBERED_PHONE, PERSONAL_CODE)).willReturn(session);

    assertThat(starter.start(null, PERSONAL_CODE, true).isRememberMe()).isTrue();
  }

  @Test
  void aSessionStartedWithATypedPhoneCarriesNoRememberedPhone() {
    var session = new MobileIDSession("mid-session", "1234", MobileIdFixture.hash, "+37251234567");
    given(authService.startLogin("5123 4567", PERSONAL_CODE)).willReturn(session);

    assertThat(starter.start("5123 4567", PERSONAL_CODE, false).getRememberedPhoneId()).isNull();
  }

  @Test
  void forgetsARememberedPhoneMobileIdSaysDoesNotBelongToThePersonAndAsksForThePhone() {
    given(rememberedPhones.find(PERSONAL_CODE))
        .willReturn(Optional.of(new RememberedMobileIdPhone(3L, REMEMBERED_PHONE)));
    given(authService.startLogin(REMEMBERED_PHONE, PERSONAL_CODE))
        .willThrow(new MobileIdNotMidClientException());

    assertThatThrownBy(() -> starter.start(null, PERSONAL_CODE, false))
        .isInstanceOf(MobileIdException.class)
        .extracting(
            e -> ((MobileIdException) e).getErrorsResponse().getErrors().getFirst().getCode())
        .isEqualTo("mobile.id.phone.number.required");
    verify(rememberedPhones).forget(3L);
  }

  @Test
  void aTypedPhoneMobileIdRejectsLeavesWhatIsRememberedAlone() {
    given(authService.startLogin("5123 4567", PERSONAL_CODE))
        .willThrow(new MobileIdNotMidClientException());

    assertThatThrownBy(() -> starter.start("5123 4567", PERSONAL_CODE, false))
        .isInstanceOf(MobileIdNotMidClientException.class);
    verifyNoInteractions(rememberedPhones);
  }

  @Test
  void withoutATypedPhoneOrARememberedOneAsksForThePhone() {
    given(rememberedPhones.find(PERSONAL_CODE)).willReturn(Optional.empty());

    assertThatThrownBy(() -> starter.start(null, PERSONAL_CODE, false))
        .isInstanceOf(MobileIdException.class)
        .extracting(
            e -> ((MobileIdException) e).getErrorsResponse().getErrors().getFirst().getCode())
        .isEqualTo("mobile.id.phone.number.required");
    verify(authService, never()).startLogin(any(), any());
  }

  @Test
  void aRememberedPhoneIsNotPushedToAgainWithinTenSeconds() {
    given(rememberedPhones.find(PERSONAL_CODE))
        .willReturn(Optional.of(new RememberedMobileIdPhone(3L, REMEMBERED_PHONE)));
    willThrow(new PushLoginStartedTooSoonException(MOBILE_ID))
        .given(rememberedPhones)
        .claimLoginStart();

    assertThatThrownBy(() -> starter.start("", PERSONAL_CODE, false))
        .isInstanceOf(PushLoginStartedTooSoonException.class);
    verify(authService, never()).startLogin(any(), any());
  }
}
