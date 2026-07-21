package ee.tuleva.onboarding.auth.role;

public record PendingOnboardingResponse(RoleType type, String code, String name) {}
