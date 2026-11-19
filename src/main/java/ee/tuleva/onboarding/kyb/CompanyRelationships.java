package ee.tuleva.onboarding.kyb;

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
}
