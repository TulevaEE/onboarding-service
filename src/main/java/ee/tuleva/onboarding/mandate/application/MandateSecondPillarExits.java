package ee.tuleva.onboarding.mandate.application;

import static ee.tuleva.onboarding.applicationtype.ApplicationType.EARLY_WITHDRAWAL;
import static ee.tuleva.onboarding.applicationtype.ApplicationType.STOP_CONTRIBUTIONS;
import static ee.tuleva.onboarding.mandate.application.ApplicationStatus.COMPLETE;
import static ee.tuleva.onboarding.mandate.application.ApplicationStatus.FAILED;
import static ee.tuleva.onboarding.user.SecondPillarExit.MONEY_WITHDRAWN;
import static ee.tuleva.onboarding.user.SecondPillarExit.PAYMENTS_STOPPED;

import ee.tuleva.onboarding.applicationtype.ApplicationType;
import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.mandate.MandateGateway;
import ee.tuleva.onboarding.user.SecondPillarExit;
import ee.tuleva.onboarding.user.SecondPillarExits;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class MandateSecondPillarExits implements SecondPillarExits {

  private final MandateGateway mandateGateway;

  @Override
  public SecondPillarExit of(Person person) {
    try {
      List<ApplicationSnapshot> applications = mandateGateway.getApplications(person);
      return realized(applications, STOP_CONTRIBUTIONS) && !filed(applications, EARLY_WITHDRAWAL)
          ? PAYMENTS_STOPPED
          : MONEY_WITHDRAWN;
    } catch (RuntimeException e) {
      log.warn("Could not read applications to tell how a person left the II pillar", e);
      return MONEY_WITHDRAWN;
    }
  }

  private static boolean realized(List<ApplicationSnapshot> applications, ApplicationType type) {
    return applications.stream()
        .anyMatch(
            application -> application.getType() == type && application.getStatus() == COMPLETE);
  }

  private static boolean filed(List<ApplicationSnapshot> applications, ApplicationType type) {
    return applications.stream()
        .anyMatch(
            application -> application.getType() == type && application.getStatus() != FAILED);
  }
}
