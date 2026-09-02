package ee.tuleva.onboarding.auth.role;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record RoleResponse(RoleType type, String code, String name, @Nullable UUID id) {}
