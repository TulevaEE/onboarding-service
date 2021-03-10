package ee.tuleva.onboarding.aml;

import static java.util.stream.Collectors.toList;

import ee.tuleva.onboarding.aml.dto.AmlCheckAddCommand;
import ee.tuleva.onboarding.aml.dto.AmlCheckResponse;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import io.swagger.annotations.ApiOperation;
import java.util.List;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import springfox.documentation.annotations.ApiIgnore;

@RestController
@RequestMapping("/v1/amlchecks")
@Slf4j
@RequiredArgsConstructor
class AmlCheckController {

  private final AmlCheckService amlCheckService;

  @GetMapping
  @ApiOperation(value = "Get missing AML checks")
  public List<AmlCheckResponse> getMissing(
      @ApiIgnore @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
    Long userId = authenticatedPerson.getUserId();
    return amlCheckService.getMissingChecks(userId).stream()
        .map(type -> AmlCheckResponse.builder().type(type).success(false).build())
        .collect(toList());
  }

  @PostMapping
  @ApiOperation(value = "Add manual AML check")
  public AmlCheckResponse addManualCheck(
      @ApiIgnore @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson,
      @Valid @RequestBody AmlCheckAddCommand command) {
    Long userId = authenticatedPerson.getUserId();
    amlCheckService.addCheckIfMissing(userId, command);
    return new AmlCheckResponse(command);
  }
}
