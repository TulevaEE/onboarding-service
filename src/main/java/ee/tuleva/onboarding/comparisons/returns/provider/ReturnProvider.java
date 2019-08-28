package ee.tuleva.onboarding.comparisons.returns.provider;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.comparisons.returns.Returns;

import java.time.Instant;

public interface ReturnProvider {

    Returns getReturns(Person person, Instant startTime, Integer pillar);

}
