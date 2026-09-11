package ee.tuleva.onboarding.auth.role;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface ChildRepresentations {
  Map<String, UUID> findActivelyRepresentedChildren(String personalCode);

  List<String> findPendingChildCodes(String personalCode);

  boolean isActiveRepresentation(String personalCode, String childCode);
}
