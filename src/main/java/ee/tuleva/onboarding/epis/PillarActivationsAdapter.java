package ee.tuleva.onboarding.epis;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.event.PillarActivation;
import ee.tuleva.onboarding.event.PillarActivations;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class PillarActivationsAdapter implements PillarActivations {

  private final ContactDetailsService contactDetailsService;

  @Override
  public PillarActivation forPerson(Person person) {
    var contactDetails = contactDetailsService.getContactDetails(person);
    return new PillarActivation(
        contactDetails.isSecondPillarActive(), contactDetails.isThirdPillarActive());
  }
}
