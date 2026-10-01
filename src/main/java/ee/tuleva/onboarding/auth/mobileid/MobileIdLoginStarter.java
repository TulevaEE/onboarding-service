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

  public MobileIDSession start(@Nullable String typedPhoneNumber, String personalCode) {
    if (isNotBlank(typedPhoneNumber)) {
      return authService.startLogin(typedPhoneNumber, personalCode);
    }
    RememberedMobileIdPhone remembered =
        rememberedPhones.find(personalCode).orElseThrow(MobileIdException::phoneNumberRequired);
    rememberedPhones.claimLoginStart();
    return authService.startLogin(remembered.phoneNumber(), personalCode);
  }
}
