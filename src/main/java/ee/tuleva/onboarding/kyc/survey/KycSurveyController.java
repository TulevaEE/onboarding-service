package ee.tuleva.onboarding.kyc.survey;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/kyc/surveys")
@RequiredArgsConstructor
class KycSurveyController {

  private final KycSurveyService kycSurveyService;

  @PostMapping
  @ResponseStatus(HttpStatus.OK)
  @Operation(summary = "Submit KYC survey")
  public void submit(
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson,
      @Valid @RequestBody KycSurveyResponse surveyResponse) {
    kycSurveyService.submit(authenticatedPerson, surveyResponse);
  }
}
