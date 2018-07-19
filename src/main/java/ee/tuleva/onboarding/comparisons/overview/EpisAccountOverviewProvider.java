package ee.tuleva.onboarding.comparisons.overview;

import ee.tuleva.onboarding.auth.principal.Person;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class EpisAccountOverviewProvider implements AccountOverviewProvider {
    @Override
    public AccountOverview getAccountOverview(Person person, Instant startTime) {
        return null;
    }
}
