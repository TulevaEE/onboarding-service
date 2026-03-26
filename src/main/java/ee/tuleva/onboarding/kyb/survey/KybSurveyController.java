package ee.tuleva.onboarding.kyb.survey;

import static org.springframework.http.HttpStatus.FORBIDDEN;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import io.swagger.v3.oas.annotations.Operation;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

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

  @ExceptionHandler(NotBoardMemberException.class)
  ResponseEntity<Map<String, String>> handleNotBoardMember(NotBoardMemberException exception) {
    return new ResponseEntity<>(
        Map.of("error", "NOT_BOARD_MEMBER", "error_description", exception.getMessage()),
        FORBIDDEN);
  }
}
