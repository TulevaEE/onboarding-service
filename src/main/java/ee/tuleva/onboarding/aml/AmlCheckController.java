package ee.tuleva.onboarding.aml;

import ee.tuleva.onboarding.aml.command.AmlCheckAddCommand;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import springfox.documentation.annotations.ApiIgnore;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/v1/amlchecks")
@Slf4j
@RequiredArgsConstructor
class AmlCheckController {

  private final AmlCheckService amlCheckService;

  @GetMapping
  @ApiOperation(value = "Get missing AML checks")
  public List<AmlCheckType> getMissing(@ApiIgnore @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
    Long userId = authenticatedPerson.getUserId();
    return amlCheckService.getMissingChecks(userId);
  }

  @PostMapping
  @ApiOperation(value = "Add manual AML check")
  public void addCheck(@ApiIgnore @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson,
                       @Valid @RequestBody AmlCheckAddCommand command) {
    Long userId = authenticatedPerson.getUserId();
    amlCheckService.addCheckIfMissing(userId, command.getType(), command.isSuccess());
  }
}
