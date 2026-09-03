package ee.tuleva.onboarding.kyb;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

public interface CompanyIdResolver {

  @Nullable UUID resolveId(RegistryCode registryCode);
}
