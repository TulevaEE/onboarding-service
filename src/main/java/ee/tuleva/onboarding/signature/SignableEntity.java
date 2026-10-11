package ee.tuleva.onboarding.signature;

import java.io.Serializable;

public record SignableEntity(String kind, Long id) implements Serializable {}
