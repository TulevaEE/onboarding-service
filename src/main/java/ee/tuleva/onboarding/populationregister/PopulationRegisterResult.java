package ee.tuleva.onboarding.populationregister;

import java.util.UUID;

public record PopulationRegisterResult<T>(T data, UUID messageId) {}
