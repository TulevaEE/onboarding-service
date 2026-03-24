package ee.tuleva.onboarding.auth.role;

import jakarta.validation.constraints.NotNull;
import java.io.Serializable;

public record Role(@NotNull RoleType type, @NotNull String code, String name)
    implements Serializable {}
