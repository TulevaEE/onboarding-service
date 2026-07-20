package ee.tuleva.onboarding.party;

import java.util.UUID;

// Thrown when a pending-link id does not resolve to a PENDING_KYC link owned by the requesting
// parent — an unknown id, someone else's link, or an already-activated one. The id is not
// authorization: resolution is always scoped to the authenticated parent.
public class PendingChildLinkNotFoundException extends RuntimeException {

  public PendingChildLinkNotFoundException(String parentPersonalCode, UUID pendingLinkId) {
    super(
        "Pending child link not found: parentPersonalCode="
            + parentPersonalCode
            + ", pendingLinkId="
            + pendingLinkId);
  }
}
