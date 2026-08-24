package ee.tuleva.onboarding.investment.instrument;

import ee.tuleva.onboarding.instrument.InstrumentReferenceChange;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@NullMarked
@RequiredArgsConstructor
class InstrumentReferenceChangeDescriber {

  private final ObjectMapper objectMapper;

  String describe(List<InstrumentReferenceChange> changes) {
    var body = new StringBuilder("Instrument reference data changed:\n\n");
    for (var change : changes) {
      body.append(
          "%s %s by %s at %s\n"
              .formatted(
                  change.operation(), change.isin(), change.changedBy(), change.changedAt()));
      for (var line : fieldLines(change)) {
        body.append("  ").append(line).append('\n');
      }
      body.append('\n');
    }
    return body.toString();
  }

  private List<String> fieldLines(InstrumentReferenceChange change) {
    var before = read(change.oldValues());
    var after = read(change.newValues());

    if (before == null) {
      return presentValues(after);
    }
    if (after == null) {
      return presentValues(before);
    }
    return differences(before, after);
  }

  private static List<String> presentValues(@Nullable JsonNode node) {
    if (node == null) {
      return List.of();
    }
    var lines = new ArrayList<String>();
    for (var fieldName : node.propertyNames()) {
      var value = node.path(fieldName);
      if (!value.isNull()) {
        lines.add("%s: %s".formatted(fieldName, text(value)));
      }
    }
    return lines;
  }

  private static List<String> differences(JsonNode before, JsonNode after) {
    var fieldNames = new LinkedHashSet<>(before.propertyNames());
    fieldNames.addAll(after.propertyNames());

    var lines = new ArrayList<String>();
    for (var fieldName : fieldNames) {
      var oldValue = before.path(fieldName);
      var newValue = after.path(fieldName);
      if (!oldValue.equals(newValue)) {
        lines.add("%s: %s -> %s".formatted(fieldName, text(oldValue), text(newValue)));
      }
    }
    return lines;
  }

  private static String text(JsonNode node) {
    return node.isMissingNode() || node.isNull() ? "null" : node.asString();
  }

  private @Nullable JsonNode read(@Nullable String json) {
    if (json == null) {
      return null;
    }
    try {
      return objectMapper.readTree(json);
    } catch (Exception e) {
      log.error("Failed to parse instrument reference history values: json={}", json, e);
      return null;
    }
  }
}
