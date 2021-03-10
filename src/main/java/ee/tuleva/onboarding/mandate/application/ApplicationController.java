package ee.tuleva.onboarding.mandate.application;

import static ee.tuleva.onboarding.mandate.application.ApplicationController.APPLICATIONS_URI;
import static java.util.stream.Collectors.toList;
import static org.springframework.web.bind.annotation.RequestMethod.GET;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.epis.mandate.ApplicationStatus;
import io.swagger.annotations.ApiOperation;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import springfox.documentation.annotations.ApiIgnore;

@Slf4j
@RestController
@RequestMapping("/v1" + APPLICATIONS_URI)
@RequiredArgsConstructor
public class ApplicationController {
  public static final String APPLICATIONS_URI = "/applications";

  private final ApplicationService applicationService;

  @ApiOperation(value = "Get applications")
  @RequestMapping(method = GET)
  public List<Application> get(
      @ApiIgnore @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson,
      @RequestParam("status") ApplicationStatus status) {
    return applicationService.get(authenticatedPerson).stream()
        .filter(application -> application.getStatus().equals(status))
        .collect(toList());
  }

  @ApiOperation(value = "Cancel an application")
  @PostMapping("/{id}/cancellations")
  public ApplicationCancellationResponse cancel(
      @ApiIgnore @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson,
      @PathVariable("id") Long applicationId) {
    log.info("Cancelling application {}", applicationId);
    return applicationService.createCancellationMandate(
        authenticatedPerson, authenticatedPerson.getUserId(), applicationId);
  }
}
