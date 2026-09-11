package ee.tuleva.onboarding.epis;

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson;
import static ee.tuleva.onboarding.epis.ContactDetailsFixture.contactDetailsFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.paymentrate.PersistedPaymentRates;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PersistedPaymentRatesAdapterTest {

  @Mock ContactDetailsService contactDetailsService;
  @InjectMocks PersistedPaymentRatesAdapter adapter;

  @Test
  void mapsCurrentAndPendingRates() {
    Person person = samplePerson();
    var contactDetails = contactDetailsFixture();
    contactDetails.setSecondPillarPaymentRates(new ContactDetails.PaymentRates(6, 4));
    given(contactDetailsService.getContactDetails(person)).willReturn(contactDetails);

    assertThat(adapter.forPerson(person)).isEqualTo(new PersistedPaymentRates.RatePair(6, 4));
  }

  @Test
  void mapsNullRatesToEmptyRatePair() {
    Person person = samplePerson();
    var contactDetails = contactDetailsFixture();
    contactDetails.setSecondPillarPaymentRates(null);
    given(contactDetailsService.getContactDetails(person)).willReturn(contactDetails);

    assertThat(adapter.forPerson(person)).isEqualTo(new PersistedPaymentRates.RatePair(null, null));
  }
}
