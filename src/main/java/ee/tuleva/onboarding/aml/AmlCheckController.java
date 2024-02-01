package ee.tuleva.onboarding.aml;

import ee.tuleva.onboarding.aml.dto.AmlCheckAddCommand;
import ee.tuleva.onboarding.aml.dto.AmlCheckResponse;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/amlchecks")
@Slf4j
@RequiredArgsConstructor
class AmlCheckController {

  private final AmlCheckService amlCheckService;

  @GetMapping
  @Operation(summary = "Get missing AML checks")
  public List<AmlCheckResponse> getMissing(
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
    return amlCheckService.getMissingChecks(authenticatedPerson).stream()
        .map(type -> AmlCheckResponse.builder().type(type).success(false).build())
        .toList();
  }

  @PostMapping
  @Operation(summary = "Add manual AML check")
  public AmlCheckResponse addManualCheck(
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson,
      @Valid @RequestBody AmlCheckAddCommand command) {
    amlCheckService.addCheckIfMissing(authenticatedPerson, command);
    return new AmlCheckResponse(command);
  }
}
