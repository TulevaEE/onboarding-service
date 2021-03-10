package ee.tuleva.onboarding.mandate.application;

import static com.google.common.collect.MoreCollectors.onlyElement;
import static java.util.stream.Collectors.toList;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.cancellation.MandateCancellationService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ApplicationService {

  private final EpisService episService;
  private final ApplicationConverter applicationConverter;
  private final MandateCancellationService mandateCancellationService;

  public List<Application> get(Person person) {
    return episService.getApplications(person).stream()
        .map(applicationConverter::convert)
        .collect(toList());
  }

  private Application getApplication(Long applicationId, Person person) {
    List<Application> applications = get(person);
    return applications.stream()
        .filter(application -> application.getId().equals(applicationId))
        .collect(onlyElement());
  }

  public ApplicationCancellationResponse createCancellationMandate(
      Person person, Long userId, Long applicationId) {
    Application applicationToCancel = getApplication(applicationId, person);
    Mandate mandate =
        mandateCancellationService.saveCancellationMandate(userId, applicationToCancel);
    return new ApplicationCancellationResponse(mandate.getId());
  }
}
