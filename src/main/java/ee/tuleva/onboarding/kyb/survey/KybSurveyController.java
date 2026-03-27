package ee.tuleva.onboarding.kyb.survey;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_IMPLEMENTED;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/v1/kyb/surveys")
@RequiredArgsConstructor
class KybSurveyController {

  private final KybSurveyService kybSurveyService;

  @Operation(summary = "Initial validation for legal entity KYB survey")
  @GetMapping("/initial-validation")
  public LegalEntityData initialValidation(
      @RequestParam(value = "registry-code") String registryCode,
      @AuthenticationPrincipal AuthenticatedPerson person) {
    return kybSurveyService.initialValidation(registryCode, person.getPersonalCode());
  }

  @Operation(summary = "Submit KYB survey")
  @PostMapping
  public void submit(
      @RequestParam(value = "registry-code") String registryCode,
      @AuthenticationPrincipal AuthenticatedPerson person,
      @Valid @RequestBody KybSurveyResponse surveyResponse) {
    kybSurveyService.submit(
        person.getUserId(), person.getPersonalCode(), registryCode, surveyResponse);
  }

  @ExceptionHandler(NotBoardMemberException.class)
  ResponseEntity<Map<String, String>> handleNotBoardMember(NotBoardMemberException exception) {
    return new ResponseEntity<>(
        Map.of("error", "NOT_BOARD_MEMBER", "error_description", exception.getMessage()),
        FORBIDDEN);
  }

  @ExceptionHandler(OnboardingNotAllowedException.class)
  ResponseEntity<Map<String, String>> handleOnboardingNotAllowed(
      OnboardingNotAllowedException exception) {
    return new ResponseEntity<>(
        Map.of("error", "ONBOARDING_NOT_ALLOWED", "error_description", exception.getMessage()),
        FORBIDDEN);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<Map<String, String>> handleUnexpectedError(Exception exception) {
    log.error("Unexpected error in KYB survey: message={}", exception.getMessage(), exception);
    return new ResponseEntity<>(
        Map.of("error", "UNEXPECTED_ERROR", "error_description", exception.getMessage()),
        NOT_IMPLEMENTED);
  }
}
