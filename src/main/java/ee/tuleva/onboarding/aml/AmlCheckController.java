package ee.tuleva.onboarding.aml;

import static java.util.stream.Collectors.toList;

import ee.tuleva.onboarding.aml.dto.AmlCheckAddCommand;
import ee.tuleva.onboarding.aml.dto.AmlCheckResponse;
import ee.tuleva.onboarding.auth.AuthenticatedPersonPrincipal;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
      @AuthenticatedPersonPrincipal AuthenticatedPerson authenticatedPerson) {
    Long userId = authenticatedPerson.getUserId();
    return amlCheckService.getMissingChecks(userId).stream()
        .map(type -> AmlCheckResponse.builder().type(type).success(false).build())
        .collect(toList());
  }

  @PostMapping
  @Operation(summary = "Add manual AML check")
  public AmlCheckResponse addManualCheck(
      @AuthenticatedPersonPrincipal AuthenticatedPerson authenticatedPerson,
      @Valid @RequestBody AmlCheckAddCommand command) {
    Long userId = authenticatedPerson.getUserId();
    amlCheckService.addCheckIfMissing(userId, command);
    return new AmlCheckResponse(command);
  }
}
