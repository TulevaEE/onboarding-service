package ee.tuleva.onboarding.kyb;

import static ee.tuleva.onboarding.kyb.KybCheckType.DATA_CHANGED;

import ee.tuleva.onboarding.ariregister.RepresentationRight;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

  public boolean hasOwnershipEvidenceChange() {
    return checks.stream()
        .filter(check -> check.type() == DATA_CHANGED)
        .flatMap(check -> changeEntries(check).stream())
        .filter(entry -> isOwnershipCheckName(entry.get("check")))
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

  private static boolean isOwnershipCheckName(Object checkName) {
    return Arrays.stream(KybCheckType.values())
        .filter(KybCheckType::isOwnershipCheck)
        .anyMatch(type -> type.name().equals(checkName));
  }
}
