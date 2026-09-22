package ee.tuleva.onboarding.epis;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.nudge.PillarActivity;
import ee.tuleva.onboarding.nudge.PillarStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class EpisPillarStatus implements PillarStatus {

  private final ContactDetailsService contactDetailsService;

  @Override
  public PillarActivity of(Person person) {
    ContactDetails contactDetails = contactDetailsService.getContactDetails(person);
    return new PillarActivity(
        contactDetails.isSecondPillarActive(), contactDetails.isThirdPillarActive());
  }
}
