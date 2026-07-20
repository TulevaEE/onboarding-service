package ee.tuleva.onboarding.auth.role;

import java.util.UUID;

// A child the authenticated parent can "join" (they are a co-guardian, but their own onboarding/KYC
// is not yet complete, so they have no access yet). The id is the pending link's opaque UUID — the
// join flow resolves it back to the child scoped to this parent, so a minor's personal code never
// travels in a URL. Distinct from Role: pending entries are shown in the switcher but NOT switchable.
public record PendingChildResponse(UUID id, String childName) {}
