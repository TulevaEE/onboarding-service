package ee.tuleva.onboarding.party;

import java.util.UUID;

public class PendingChildLinkNotFoundException extends RuntimeException {

  public PendingChildLinkNotFoundException(String parentPersonalCode, UUID pendingLinkId) {
    super(
        "Pending child link not found: parentPersonalCode="
            + parentPersonalCode
            + ", pendingLinkId="
            + pendingLinkId);
  }
}
