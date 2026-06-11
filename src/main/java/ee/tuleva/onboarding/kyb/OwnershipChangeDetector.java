package ee.tuleva.onboarding.kyb;

import static java.util.stream.Collectors.toSet;

import ee.tuleva.onboarding.ariregister.CompanyRelationship;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class OwnershipChangeDetector {

  private static final String SHAREHOLDER_ROLE = "OSAN";

  public boolean ownerChangedBeforeOnboarding(List<CompanyRelationship> relationshipHistory) {
    Set<String> currentOwners =
        relationshipHistory.stream()
            .filter(this::isOwner)
            .filter(this::isActive)
            .map(CompanyRelationship::personalCode)
            .collect(toSet());

    return relationshipHistory.stream()
        .filter(this::isOwner)
        .map(CompanyRelationship::personalCode)
        .anyMatch(code -> code != null && !currentOwners.contains(code));
  }

  private boolean isOwner(CompanyRelationship relationship) {
    return SHAREHOLDER_ROLE.equals(relationship.roleCode()) || relationship.controlMethod() != null;
  }

  private boolean isActive(CompanyRelationship relationship) {
    return relationship.endDate() == null;
  }
}
