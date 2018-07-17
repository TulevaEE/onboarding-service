package ee.tuleva.onboarding.comparisons;

import ee.tuleva.onboarding.auth.principal.Person;

import java.time.Instant;

public interface AccountOverviewProvider {
    AccountOverview getAccountOverview(Person person, Instant startTime);
}
