package ee.tuleva.onboarding.kyb.survey;

import static ee.tuleva.onboarding.auth.role.RoleType.LEGAL_ENTITY;
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
  public LegalEntityData initialValidation(@AuthenticationPrincipal AuthenticatedPerson person) {
    var registryCode = requireLegalEntityRole(person);
    return kybSurveyService.initialValidation(registryCode, person.getPersonalCode());
  }

  @ExceptionHandler(LegalEntityRoleRequiredException.class)
  ResponseEntity<Map<String, String>> handleLegalEntityRoleRequired(
      LegalEntityRoleRequiredException exception) {
    return new ResponseEntity<>(
        Map.of("error", "LEGAL_ENTITY_ROLE_REQUIRED", "error_description", exception.getMessage()),
        FORBIDDEN);
  }

  private String requireLegalEntityRole(AuthenticatedPerson person) {
    if (person.getRole().type() == LEGAL_ENTITY) {
      return person.getRole().code();
    }
    throw new LegalEntityRoleRequiredException(person.getPersonalCode());
  }
}
