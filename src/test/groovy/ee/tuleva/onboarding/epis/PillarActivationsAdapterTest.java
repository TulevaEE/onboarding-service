package ee.tuleva.onboarding.epis;

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.event.PillarActivation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PillarActivationsAdapterTest {

  @Mock ContactDetailsService contactDetailsService;

  PillarActivationsAdapter pillarActivationsAdapter;

  @BeforeEach
  void setUp() {
    pillarActivationsAdapter = new PillarActivationsAdapter(contactDetailsService);
  }

  @Test
  void forPerson_returnsActivationFlagsFromContactDetails() {
    Person person = samplePerson();
    ContactDetails contactDetails =
        ContactDetails.builder().isSecondPillarActive(true).isThirdPillarActive(false).build();
    given(contactDetailsService.getContactDetails(person)).willReturn(contactDetails);

    PillarActivation result = pillarActivationsAdapter.forPerson(person);

    assertThat(result).isEqualTo(new PillarActivation(true, false));
  }
}
