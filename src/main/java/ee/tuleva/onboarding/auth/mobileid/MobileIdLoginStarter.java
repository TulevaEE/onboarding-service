package ee.tuleva.onboarding.auth.mobileid;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MobileIdLoginStarter {

  private final MobileIdAuthService authService;
  private final RememberedMobileIdPhones rememberedPhones;

  public MobileIDSession start(
      @Nullable String typedPhoneNumber, String personalCode, boolean rememberMe) {
    MobileIDSession session = startWithTypedOrRememberedPhone(typedPhoneNumber, personalCode);
    session.setRememberMe(rememberMe);
    return session;
  }

  private MobileIDSession startWithTypedOrRememberedPhone(
      @Nullable String typedPhoneNumber, String personalCode) {
    if (isNotBlank(typedPhoneNumber)) {
      return authService.startLogin(typedPhoneNumber, personalCode);
    }
    RememberedMobileIdPhone remembered =
        rememberedPhones.find(personalCode).orElseThrow(MobileIdException::phoneNumberRequired);
    rememberedPhones.claimLoginStart();
    try {
      MobileIDSession session = authService.startLogin(remembered.phoneNumber(), personalCode);
      session.setRememberedPhoneId(remembered.id());
      return session;
    } catch (MobileIdNotMidClientException e) {
      rememberedPhones.forget(remembered.id());
      throw MobileIdException.phoneNumberRequired();
    }
  }
}
