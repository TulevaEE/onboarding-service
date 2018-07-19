package ee.tuleva.onboarding.comparisons.overview;

import ee.tuleva.onboarding.auth.principal.Person;
import org.springframework.stereotype.Service;

import java.time.Instant;

// TODO: delete this once we have implemented an account overview provider, this is just here so spring boots.
@Service
public class DummyAccountOverviewProvider implements AccountOverviewProvider {
    @Override
    public AccountOverview getAccountOverview(Person person, Instant startTime) {
        return null;
    }
}
