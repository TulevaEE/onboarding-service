package ee.tuleva.onboarding.kyb;

import java.util.Map;

public record KybCheck(KybCheckType type, boolean success, Map<String, Object> metadata) {}
