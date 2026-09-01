package ee.tuleva.onboarding.epis;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.paymentrate.PersistedPaymentRates;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class PersistedPaymentRatesAdapter implements PersistedPaymentRates {

  private final ContactDetailsService contactDetailsService;

  @Override
  public RatePair forPerson(Person person) {
    var rates = contactDetailsService.getContactDetails(person).getSecondPillarPaymentRates();
    return rates != null
        ? new RatePair(rates.getCurrent(), rates.getPending())
        : new RatePair(null, null);
  }
}
