package ee.tuleva.onboarding.kyb;

import static ee.tuleva.onboarding.kyb.KybCheckType.DATA_CHANGED;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KybDataChangeDetector {

  private final KybCheckHistory checkHistory;

  public KybCheck detect(PersonalCode personalCode, List<KybCheck> currentChecks) {
    var previousChecks = checkHistory.getLatestChecks(personalCode);

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
      if (previous == null) {
        changes.add(
            Map.of(
                "check", current.type().name(),
                "previousSuccess", "N/A",
                "currentSuccess", current.success()));
      } else if (previous.success() != current.success()
          || !previous.metadata().equals(current.metadata())) {
        changes.add(
            Map.of(
                "check", current.type().name(),
                "previousSuccess", previous.success(),
                "currentSuccess", current.success()));
      }
    }

    for (var previous : previousChecks) {
      if (!currentByType.containsKey(previous.type())) {
        changes.add(
            Map.of(
                "check", previous.type().name(),
                "previousSuccess", previous.success(),
                "currentSuccess", "N/A"));
      }
    }

    return new KybCheck(DATA_CHANGED, changes.isEmpty(), Map.of("changes", changes));
  }
}
