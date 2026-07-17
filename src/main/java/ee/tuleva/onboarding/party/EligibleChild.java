package ee.tuleva.onboarding.party;

import org.jspecify.annotations.Nullable;

// A child the authenticated parent may open a savings fund account for. The name comes from the
// population register's custody response and may be absent, so it is nullable.
record EligibleChild(
    String personalCode,
    @Nullable String firstName,
    @Nullable String lastName,
    boolean hasBeenOnboarded) {}
