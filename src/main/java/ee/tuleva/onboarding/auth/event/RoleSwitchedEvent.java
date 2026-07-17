package ee.tuleva.onboarding.auth.event;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import org.jspecify.annotations.NullMarked;

@NullMarked
public record RoleSwitchedEvent(AuthenticatedPerson person) {}
