package ee.tuleva.onboarding.kyc.survey;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/kyc/identity")
@RequiredArgsConstructor
class KycIdentityController {

  private final KycSurveyService kycSurveyService;

  @GetMapping
  @Operation(summary = "Get the latest KYC identity of the authenticated person")
  public KycIdentityResponse getIdentity(
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
    return kycSurveyService.getIdentity(authenticatedPerson.getUserId());
  }
}
