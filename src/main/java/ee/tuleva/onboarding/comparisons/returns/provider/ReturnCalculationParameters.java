package ee.tuleva.onboarding.comparisons.returns.provider;

import ee.tuleva.onboarding.auth.principal.Person;
import java.time.Instant;
import java.util.List;

public record ReturnCalculationParameters(
    Person person, Instant startTime, Integer pillar, List<String> keys) {}
