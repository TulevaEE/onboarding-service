package ee.tuleva.onboarding.paymentrate;

import ee.tuleva.onboarding.auth.principal.Person;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SecondPillarPaymentRateService {

  private static final int DEFAULT_SECOND_PILLAR_PAYMENT_RATE = 2;
  private final PersistedPaymentRates persistedPaymentRates;

  public PaymentRates getPaymentRates(Person person) {
    PersistedPaymentRates.RatePair rates = persistedPaymentRates.forPerson(person);

    Integer current = rates.current();
    Integer pending = rates.pending();

    return new PaymentRates(
        current != null ? current : DEFAULT_SECOND_PILLAR_PAYMENT_RATE, pending);
  }
}
