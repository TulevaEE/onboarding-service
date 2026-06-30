package ee.tuleva.onboarding.party;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/me/children")
@RequiredArgsConstructor
class ChildController {

  private final ChildOnboardingService childOnboardingService;

  @PostMapping
  @Operation(summary = "Open a savings fund account for a represented child")
  public ResponseEntity<ChildResponse> openForChild(
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson,
      @Valid @RequestBody CreateChildCommand command) {
    ChildOnboardingResult result =
        childOnboardingService.onboardChild(authenticatedPerson, command.childPersonalCode());
    if (result.verified()) {
      return ResponseEntity.ok(ChildResponse.verified(result));
    }
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(ChildResponse.underReview());
  }
}
