package ee.tuleva.onboarding.auth.mobileid;

import static org.springframework.http.HttpStatus.NO_CONTENT;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
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

  @GetMapping("/remembered-person")
  public ResponseEntity<RememberedMobileIdPersonResponse> rememberedPerson() {
    return rememberedPhones
        .mostRecentPerson()
        .map(RememberedMobileIdPersonResponse::from)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.noContent().build());
  }

  @DeleteMapping("/remembered-person")
  @ResponseStatus(NO_CONTENT)
  public void forgetRememberedPerson() {
    rememberedPhones.forgetMostRecentPerson();
  }
}
