package ee.tuleva.onboarding.mandate.application;

import static ee.tuleva.onboarding.applicationtype.ApplicationType.EARLY_WITHDRAWAL;
import static ee.tuleva.onboarding.mandate.application.ApplicationStatus.COMPLETE;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.nudge.SecondPillarEarlyWithdrawals;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class MandateEarlyWithdrawals implements SecondPillarEarlyWithdrawals {

  private final ApplicationService applicationService;

  @Override
  public boolean hasCompleted(Person person) {
    return applicationService.getWithdrawalApplications(COMPLETE, person).stream()
        .anyMatch(application -> application.getType() == EARLY_WITHDRAWAL);
  }
}
