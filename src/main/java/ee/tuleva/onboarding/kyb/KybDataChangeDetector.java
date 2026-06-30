package ee.tuleva.onboarding.kyb;

import static ee.tuleva.onboarding.kyb.KybCheckType.COMPANY_PEP;
import static ee.tuleva.onboarding.kyb.KybCheckType.COMPANY_SANCTION;
import static ee.tuleva.onboarding.kyb.KybCheckType.DATA_CHANGED;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KybDataChangeDetector {

  private static final Set<KybCheckType> AUDIT_ONLY_METADATA =
      Set.of(COMPANY_SANCTION, COMPANY_PEP);

  private final KybCheckHistory checkHistory;

  public KybCheck detect(
      PersonalCode personalCode, RegistryCode registryCode, List<KybCheck> currentChecks) {
    var previousChecks = checkHistory.getLatestChecks(personalCode, registryCode);

    if (previousChecks.isEmpty()) {
      return new KybCheck(DATA_CHANGED, true, Map.of("changes", List.of()));
    }

    var previousByType =
        previousChecks.stream()
            .collect(
                Collectors.toMap(KybCheck::type, Function.identity(), (first, second) -> first));

    var currentByType =
        currentChecks.stream().collect(Collectors.toMap(KybCheck::type, Function.identity()));

    var changes = new ArrayList<Map<String, Object>>();

    for (var current : currentChecks) {
      var previous = previousByType.get(current.type());
      if (isExistingCheck(previous) && changed(previous, current)) {
        changes.add(change(current.type(), previous.success(), current.success()));
      }
    }

    for (var previous : previousChecks) {
      if (isRemovedCheck(previous, currentByType)) {
        changes.add(change(previous.type(), previous.success(), "N/A"));
      }
    }

    return new KybCheck(DATA_CHANGED, changes.isEmpty(), Map.of("changes", changes));
  }

  private boolean isExistingCheck(KybCheck previous) {
    return previous != null;
  }

  private boolean isRemovedCheck(KybCheck previous, Map<KybCheckType, KybCheck> currentByType) {
    return !currentByType.containsKey(previous.type());
  }

  private Map<String, Object> change(
      KybCheckType type, Object previousSuccess, Object currentSuccess) {
    return Map.of(
        "check", type.name(),
        "previousSuccess", previousSuccess,
        "currentSuccess", currentSuccess);
  }

  private boolean changed(KybCheck previous, KybCheck current) {
    if (previous.success() != current.success()) {
      return true;
    }
    return !AUDIT_ONLY_METADATA.contains(current.type())
        && !previous.metadata().equals(current.metadata());
  }
}
