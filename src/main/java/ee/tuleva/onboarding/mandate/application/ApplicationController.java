package ee.tuleva.onboarding.mandate.application;

import static ee.tuleva.onboarding.mandate.application.ApplicationController.APPLICATIONS_URI;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.epis.mandate.ApplicationStatus;
import io.swagger.v3.oas.annotations.Operation;
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

@Slf4j
@RestController
@RequestMapping("/v1" + APPLICATIONS_URI)
@RequiredArgsConstructor
class ApplicationController {

  public static final String APPLICATIONS_URI = "/applications";

  private final ApplicationCancellationService applicationCancellationService;
  private final ApplicationService applicationService;

  @Operation(summary = "Get application")
  @GetMapping("/{id}")
  public Application<?> getApplication(
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson, @PathVariable Long id) {
    return applicationService.getApplication(id, authenticatedPerson);
  }

  @Operation(summary = "Get applications")
  @GetMapping
  public List<Application<?>> getApplications(
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson,
      @RequestParam ApplicationStatus status) {
    return applicationService.getApplications(status, authenticatedPerson);
  }

  @Operation(summary = "Cancel an application")
  @PostMapping("/{id}/cancellations")
  public ApplicationCancellationResponse cancel(
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson,
      @PathVariable("id") Long applicationId) {
    log.info("Cancelling application {}", applicationId);
    return applicationCancellationService.createCancellationMandate(
        authenticatedPerson, applicationId);
  }
}
