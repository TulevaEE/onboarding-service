package ee.tuleva.onboarding.auth.role;

import java.util.List;

public interface ChildRepresentations {
  List<String> findActivelyRepresentedChildCodes(String personalCode);

  List<String> findPendingChildCodes(String personalCode);

  boolean isActiveRepresentation(String personalCode, String childCode);
}
