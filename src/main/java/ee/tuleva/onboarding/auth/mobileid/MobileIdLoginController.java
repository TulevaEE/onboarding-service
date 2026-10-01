package ee.tuleva.onboarding.auth.mobileid;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/mobile-id/login")
@RequiredArgsConstructor
public class MobileIdLoginController {

  private final RememberedMobileIdPhones rememberedPhones;

  @PostMapping("/remembered")
  public RememberedPhoneResponse remembered(@Valid @RequestBody RememberedPhoneQuery query) {
    return new RememberedPhoneResponse(rememberedPhones.isRemembered(query.personalCode()));
  }
}
