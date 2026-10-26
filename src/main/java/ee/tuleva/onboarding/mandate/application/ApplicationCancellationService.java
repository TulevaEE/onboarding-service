package ee.tuleva.onboarding.mandate.application;

import static ee.tuleva.onboarding.applicationtype.ApplicationType.REJOIN;
import static ee.tuleva.onboarding.mandate.application.ApplicationStatus.PENDING;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.deadline.MandateDeadlinesService;
import ee.tuleva.onboarding.error.NotFoundException;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.MandateGateway;
import ee.tuleva.onboarding.mandate.MandateService;
import ee.tuleva.onboarding.mandate.exception.InvalidMandateException;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ApplicationCancellationService {

  private final MandateService mandateService;
  private final MandateGateway mandateGateway;
  private final MandateDeadlinesService mandateDeadlinesService;
  private final RejoinCancellations rejoinCancellations;
  private final Clock clock;

  public ApplicationCancellationResponse createCancellationMandate(
      AuthenticatedPerson authenticatedPerson, Long applicationId) {
    ApplicationSnapshot applicationToCancel = getApplication(applicationId, authenticatedPerson);
    if (applicationToCancel.getType() == REJOIN) {
      requireCancelableRejoin(authenticatedPerson, applicationToCancel);
    }
    Mandate mandate = mandateService.saveCancellation(authenticatedPerson, applicationToCancel);
    return new ApplicationCancellationResponse(mandate.getIdOrThrow());
  }

  private void requireCancelableRejoin(AuthenticatedPerson person, ApplicationSnapshot rejoin) {
    if (rejoin.getStatus() != PENDING) {
      throw InvalidMandateException.rejoinNotPending();
    }
    if (filingWindowHasClosed(rejoin)) {
      throw InvalidMandateException.rejoinCancellationDeadlinePassed();
    }
    if (rejoinCancellations.isCanceled(person, rejoin)) {
      throw InvalidMandateException.rejoinAlreadyCanceled();
    }
  }

  private boolean filingWindowHasClosed(ApplicationSnapshot rejoin) {
    var cancellationDeadline =
        mandateDeadlinesService.getDeadlines(rejoin.getDate()).getCancellationDeadline(REJOIN);
    return clock.instant().isAfter(cancellationDeadline);
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
