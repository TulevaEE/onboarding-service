package ee.tuleva.onboarding.kyb;

import java.util.List;
import org.jspecify.annotations.Nullable;

public record CompanyDto(
    RegistryCode registryCode,
    String name,
    List<String> activityCodes,
    @Nullable LegalForm legalForm) {}
