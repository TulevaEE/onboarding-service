package ee.tuleva.onboarding.mandate.application;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.mandate.ApplicationDTO;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.cancellation.MandateCancellationService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ApplicationCancellationService {

  private final MandateCancellationService mandateCancellationService;
  private final EpisService episService;

  public ApplicationCancellationResponse createCancellationMandate(
      AuthenticatedPerson authenticatedPerson, Long applicationId) {
    ApplicationDTO applicationToCancel = getApplication(applicationId, authenticatedPerson);
    Mandate mandate =
        mandateCancellationService.saveCancellationMandate(
            authenticatedPerson, applicationToCancel);
    return new ApplicationCancellationResponse(mandate.getId());
  }

  private ApplicationDTO getApplication(Long applicationId, Person person) {
    List<ApplicationDTO> applications = episService.getApplications(person);
    return applications.stream()
        .filter(application -> application.getId().equals(applicationId))
        .findFirst()
        .orElse(null);
  }
}
