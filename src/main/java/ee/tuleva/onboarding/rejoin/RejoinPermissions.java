package ee.tuleva.onboarding.rejoin;

import static ee.tuleva.onboarding.rejoin.RejoinDecision.CONTRIBUTIONS_CANNOT_RESTART;
import static ee.tuleva.onboarding.rejoin.RejoinDecision.PERMITTED;
import static ee.tuleva.onboarding.rejoin.RejoinDecision.REFUSED_BY_PENSIONIKESKUS;

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

  RejoinDecision decideFor(Person person) {
    if (secondPillarLeavers.contributionsCannotRestart(person.getRepresentedPersonalCode())) {
      return CONTRIBUTIONS_CANNOT_RESTART;
    }
    return rejoinPermissionSource.getRejoinPermission().permitted()
        ? PERMITTED
        : REFUSED_BY_PENSIONIKESKUS;
  }
}
