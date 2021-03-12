package ee.tuleva.onboarding.mandate.application;

import static ee.tuleva.onboarding.mandate.application.ApplicationController.APPLICATIONS_URI;
import static java.util.stream.Collectors.toList;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.epis.mandate.ApplicationStatus;
import ee.tuleva.onboarding.mandate.exception.NotFoundException;
import io.swagger.annotations.ApiOperation;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import springfox.documentation.annotations.ApiIgnore;

@Slf4j
@RestController
@RequestMapping("/v1" + APPLICATIONS_URI)
@RequiredArgsConstructor
public class ApplicationController {

  public static final String APPLICATIONS_URI = "/applications";

  private final ApplicationCancellationService applicationCancellationService;
  private final ApplicationService applicationService;

  @ApiOperation(value = "Get application")
  @GetMapping("/{id}")
  public Application getApplication(
      @ApiIgnore @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson,
      @PathVariable Long id) {
    return applicationService.getApplications(authenticatedPerson).stream()
        .filter(application -> application.getId().equals(id))
        .findFirst()
        .orElseThrow(() -> new NotFoundException("Application not found: id=" + id));
  }

  @ApiOperation(value = "Get applications")
  @GetMapping
  public List<Application> getApplications(
      @ApiIgnore @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson,
      @RequestParam("status") ApplicationStatus status) {
    return applicationService.getApplications(authenticatedPerson).stream()
        .filter(application -> application.getStatus().equals(status))
        .collect(toList());
  }

  @ApiOperation(value = "Cancel an application")
  @PostMapping("/{id}/cancellations")
  public ApplicationCancellationResponse cancel(
      @ApiIgnore @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson,
      @PathVariable("id") Long applicationId) {
    log.info("Cancelling application {}", applicationId);
    return applicationCancellationService.createCancellationMandate(
        authenticatedPerson, authenticatedPerson.getUserId(), applicationId);
  }
}
