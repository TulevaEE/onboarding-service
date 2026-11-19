package ee.tuleva.onboarding.kyb;

import static ee.tuleva.onboarding.kyb.KybRelationshipRoles.BENEFICIAL_OWNER_ROLE;
import static ee.tuleva.onboarding.kyb.KybRelationshipRoles.SHAREHOLDER_ROLES;
import static ee.tuleva.onboarding.kyb.OwnershipRole.BENEFICIAL_OWNER;
import static ee.tuleva.onboarding.kyb.OwnershipRole.SHAREHOLDER;

import ee.tuleva.onboarding.ariregister.CompanyRelationship;
import java.time.LocalDate;
import java.util.List;

public record CompanyRelationships(List<CompanyRelationship> registered, LocalDate asOf) {

  public CompanyRelationships {
    registered = List.copyOf(registered);
  }

  public List<CompanyRelationship> active() {
    return registered.stream()
        .filter(r -> r.startDate() == null || !r.startDate().isAfter(asOf))
        .filter(r -> r.endDate() == null || !r.endDate().isBefore(asOf))
        .toList();
  }

  List<KybOwnership> ownerships() {
    return registered.stream()
        .filter(
            r ->
                SHAREHOLDER_ROLES.contains(r.roleCode())
                    || BENEFICIAL_OWNER_ROLE.equals(r.roleCode()))
        .map(
            r ->
                new KybOwnership(
                    r.personalCode(),
                    SHAREHOLDER_ROLES.contains(r.roleCode()) ? SHAREHOLDER : BENEFICIAL_OWNER,
                    r.startDate(),
                    r.endDate()))
        .toList();
  }
}
