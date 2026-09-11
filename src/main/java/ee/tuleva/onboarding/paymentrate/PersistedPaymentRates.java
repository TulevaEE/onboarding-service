package ee.tuleva.onboarding.paymentrate;

import ee.tuleva.onboarding.auth.principal.Person;
import org.jspecify.annotations.Nullable;

@FunctionalInterface
public interface PersistedPaymentRates {

  RatePair forPerson(Person person);

  record RatePair(@Nullable Integer current, @Nullable Integer pending) {}
}
