package ee.tuleva.onboarding.hackathon;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/hackathon-registration")
@RequiredArgsConstructor
public class HackathonRegistrationController {

  private final HackathonRegistrationService hackathonRegistrationService;

  @GetMapping
  @Operation(summary = "Get my hackathon registration, prefilled from my profile if I have none")
  public HackathonRegistrationDto getRegistration(
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
    return hackathonRegistrationService.getRegistration(authenticatedPerson);
  }

  @PostMapping
  @Operation(summary = "Create or update my hackathon registration")
  public HackathonRegistrationDto register(
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson,
      @Valid @RequestBody HackathonRegistrationRequest request) {
    return hackathonRegistrationService.register(authenticatedPerson, request);
  }
}
