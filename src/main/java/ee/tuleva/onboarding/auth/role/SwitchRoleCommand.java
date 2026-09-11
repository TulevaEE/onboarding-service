package ee.tuleva.onboarding.auth.role;

import jakarta.validation.constraints.NotNull;

record SwitchRoleCommand(@NotNull RoleType type, @NotNull String code) {}
