package ee.tuleva.onboarding.mandate.application;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.error.NotFoundException;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.MandateGateway;
import ee.tuleva.onboarding.mandate.MandateService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ApplicationCancellationService {

  private final MandateService mandateService;
  private final MandateGateway mandateGateway;

  public ApplicationCancellationResponse createCancellationMandate(
      AuthenticatedPerson authenticatedPerson, Long applicationId) {
    ApplicationSnapshot applicationToCancel = getApplication(applicationId, authenticatedPerson);
    Mandate mandate = mandateService.saveCancellation(authenticatedPerson, applicationToCancel);
    return new ApplicationCancellationResponse(mandate.getIdOrThrow());
  }

  private ApplicationSnapshot getApplication(Long applicationId, Person person) {
    List<ApplicationSnapshot> applications = mandateGateway.getApplications(person);
    return applications.stream()
        .filter(application -> application.getId().equals(applicationId))
        .findFirst()
        .orElseThrow(
            () -> new NotFoundException("Application not found: applicationId=" + applicationId));
  }
}
