package ee.tuleva.onboarding.kyb;

import org.jspecify.annotations.Nullable;

public record CompanyDto(
    RegistryCode registryCode,
    String name,
    @Nullable String naceCode,
    @Nullable LegalForm legalForm) {}
