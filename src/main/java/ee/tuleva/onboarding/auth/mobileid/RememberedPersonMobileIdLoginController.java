package ee.tuleva.onboarding.auth.mobileid;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import ee.tuleva.onboarding.auth.response.AuthenticateResponse;
import ee.tuleva.onboarding.auth.session.GenericSessionStore;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class RememberedPersonMobileIdLoginController {

  private final MobileIdLoginStarter loginStarter;
  private final GenericSessionStore sessionStore;

  @PostMapping(value = "/v1/mobile-id/login/remembered-person", consumes = APPLICATION_JSON_VALUE)
  public AuthenticateResponse continueAsRememberedPerson() {
    MobileIDSession session = loginStarter.startForRememberedPerson();
    sessionStore.save(session);
    return AuthenticateResponse.fromMobileIdSession(session);
  }
}
