package ee.tuleva.onboarding.kyb;

import static ee.tuleva.onboarding.kyb.KybCheckType.DATA_CHANGED;
import static java.util.stream.Collectors.toSet;

import ee.tuleva.onboarding.ariregister.RepresentationRight;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class KybCheckPerformedEvent extends ApplicationEvent {

  private final CompanyDto company;
  // TODO: consider relying only on CompanyDTO ?
  private final PersonalCode personalCode;
  private final List<KybRelatedPerson> relatedPersons;
  private final List<KybCheck> checks;
  private final List<RepresentationRight> representationRights;

  public KybCheckPerformedEvent(
      Object source,
      CompanyDto company,
      PersonalCode personalCode,
      List<KybRelatedPerson> relatedPersons,
      List<KybCheck> checks,
      List<RepresentationRight> representationRights) {
    super(source);
    this.company = Objects.requireNonNull(company);
    this.personalCode = Objects.requireNonNull(personalCode);
    this.relatedPersons = Objects.requireNonNull(relatedPersons);
    this.checks = Objects.requireNonNull(checks);
    this.representationRights = Objects.requireNonNull(representationRights);
  }

  public boolean hasMetadataChangeFor(Collection<KybCheckType> checkTypes) {
    Set<String> checkNames = checkTypes.stream().map(KybCheckType::name).collect(toSet());
    return checks.stream()
        .filter(check -> check.type() == DATA_CHANGED)
        .flatMap(check -> changeEntries(check).stream())
        .filter(
            entry ->
                entry.get("check") instanceof String checkName && checkNames.contains(checkName))
        .anyMatch(entry -> Boolean.TRUE.equals(entry.get("metadataChanged")));
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> changeEntries(KybCheck dataChanged) {
    if (dataChanged.metadata().get("changes") instanceof List<?> changes) {
      return changes.stream()
          .filter(Map.class::isInstance)
          .map(entry -> (Map<String, Object>) entry)
          .toList();
    }
    return List.of();
  }
}
