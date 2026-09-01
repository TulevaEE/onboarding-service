package ee.tuleva.onboarding.conversion;

import ee.tuleva.onboarding.auth.principal.Person;
import java.util.List;

public interface ConversionHoldings {

  List<ConversionHolding> forPerson(Person person);
}
