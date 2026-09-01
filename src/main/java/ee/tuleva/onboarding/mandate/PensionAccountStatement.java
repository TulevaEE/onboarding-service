package ee.tuleva.onboarding.mandate;

import ee.tuleva.onboarding.auth.principal.Person;
import java.math.BigDecimal;
import java.util.List;

@FunctionalInterface
public interface PensionAccountStatement {

  List<PensionFundBalance> forPerson(Person person);

  record PensionFundBalance(String isin, BigDecimal units, boolean activeContributions) {}
}
