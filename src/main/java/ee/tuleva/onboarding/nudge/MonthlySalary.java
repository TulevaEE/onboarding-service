package ee.tuleva.onboarding.nudge;

import ee.tuleva.onboarding.auth.principal.Person;
import java.math.BigDecimal;
import java.util.Optional;

@FunctionalInterface
public interface MonthlySalary {

  Optional<BigDecimal> latestGross(Person person, int secondPillarPaymentRate);
}
