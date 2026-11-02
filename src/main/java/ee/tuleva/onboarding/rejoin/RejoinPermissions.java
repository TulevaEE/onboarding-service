package ee.tuleva.onboarding.rejoin;

import ee.tuleva.onboarding.analytics.SecondPillarLeavers;
import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.epis.RejoinPermissionSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class RejoinPermissions {

  private final RejoinPermissionSource rejoinPermissionSource;
  private final SecondPillarLeavers secondPillarLeavers;

  RejoinPermissionResponse forPerson(Person person) {
    if (secondPillarLeavers.contributionsEndedWithoutApplication(
        person.getRepresentedPersonalCode())) {
      return new RejoinPermissionResponse(false, true);
    }
    return new RejoinPermissionResponse(
        rejoinPermissionSource.getRejoinPermission().permitted(), false);
  }
}
